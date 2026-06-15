import urllib.request, json, time

def post(path, body=None):
    url = "http://localhost:8080/api" + path
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type":"application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=5) as r:
        return json.loads(r.read())

def get(path):
    with urllib.request.urlopen("http://localhost:8080/api"+path, timeout=5) as r:
        return json.loads(r.read())

print("=== Step 1: 应用CENTER舞台中央烟雾 ===")
r = post("/stage/smoke/apply", {
    "targetZone":"CENTER", "smokeFlowDirection":"STATIC",
    "smokeDensityPercent":80, "containmentStrengthPercent":90,
    "audienceProtectionEnabled":True, "durationSeconds":0
})
print(f"Apply: success={r['success']}  desc={r['windPatternDescription'][:60]}")
time.sleep(0.4)  # 等debounce

print("\n=== Step 2: 查询初始状态（所有风机应该是同一个基准风速）===")
s = get("/stage/status")
baseline_speeds = [f['speedPercent'] for f in s['fanStatuses']]
print(f"基准风速8台: {baseline_speeds}")

print("\n=== Step 3: 开启音乐律动模式，初始90dB ===")
r = post("/stage/music/config", {"enabled": True, "initialDecibels": 90})
print(f"Music status: enabled={r['enabled']}  db={r['currentDecibels']}dB  factor={r['decibelFactor']:.2f}  running={r['loopRunning']}  maxWobble=±{r['maxWobblePercent']}%")

print("\n=== Step 4: 等1秒让tick跳动10次，然后采集4次风速（每300ms一次）观察跳动 ===")
for sample in range(4):
    time.sleep(0.3)
    s = get("/stage/status")
    speeds = [f['speedPercent'] for f in s['fanStatuses']]
    diffs = [speeds[i] - baseline_speeds[i] for i in range(8)]
    maxdiff = max(abs(d) for d in diffs)
    print(f"采样{sample+1}: {speeds}  偏离基准={diffs}  最大偏离=±{maxdiff}%  (最大允许±10%×{90/120:.0%}=±{int(10*90/120)}%)")

print("\n=== Step 5: 分贝拉满到120dB，等500ms后观察最大跳动 ===")
r = post("/stage/music/beat", {"decibels": 120})
print(f"Beat回包: db={r['currentDecibels']}dB  factor={r['decibelFactor']:.2f}")
time.sleep(0.5)
s = get("/stage/status")
speeds = [f['speedPercent'] for f in s['fanStatuses']]
diffs = [speeds[i] - baseline_speeds[i] for i in range(8)]
print(f"采样满分贝: {speeds}")
print(f"偏离基准:   {diffs}  最大偏离=±{max(abs(d) for d in diffs)}%  (最大允许±10%)")

print("\n=== Step 6: 关闭律动模式，查询status确认恢复基准 ===")
r = post("/stage/music/config", {"enabled": False})
print(f"关闭: enabled={r['enabled']}  running={r['loopRunning']}")
time.sleep(0.3)
s = get("/stage/status")
speeds = [f['speedPercent'] for f in s['fanStatuses']]
diffs = [speeds[i] - baseline_speeds[i] for i in range(8)]
print(f"关闭后风速: {speeds}")
print(f"偏离基准:   {diffs}   (应该全是0)")

print("\n✅ 音乐律动功能验证完成！")
