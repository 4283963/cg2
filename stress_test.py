import urllib.request, json, time

zones = ["LEFT_BACK", "RIGHT_BACK", "LEFT_BACK", "RIGHT_BACK", "LEFT_BACK", "RIGHT_BACK", "LEFT_FRONT", "RIGHT_FRONT", "CENTER", "FULL_STAGE"]
dirs  = ["STATIC",    "CIRCULATE_CW", "CIRCULATE_CCW", "N", "S", "E", "W", "NE", "SW", "STATIC"]
base = "http://localhost:8080/api/stage/smoke/apply"

print(f"=== 压力测试：{len(zones)}次快速切换，每次间隔50ms ===\n")
for i, (z, d) in enumerate(zip(zones, dirs)):
    body = json.dumps({
        "targetZone": z,
        "smokeFlowDirection": d,
        "smokeDensityPercent": 60 + i*3,
        "containmentStrengthPercent": 80,
        "audienceProtectionEnabled": True,
        "durationSeconds": 0
    }).encode("utf-8")
    req = urllib.request.Request(base, data=body, headers={"Content-Type": "application/json"}, method="POST")
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            resp = json.loads(r.read())
            dt = int((time.time()-t0)*1000)
            msg = resp.get("windPatternDescription", "")[:40]
            print(f"[{i+1:2d}] {dt:4d}ms  ok={str(resp['success']):5s} zone={z:12s} dir={d:14s} {msg}")
    except Exception as e:
        print(f"[{i+1:2d}] ERROR: {e}")
    time.sleep(0.05)

print("\n=== 等待600ms让debounce和批处理完成 ===")
time.sleep(0.6)

with urllib.request.urlopen("http://localhost:8080/api/stage/status", timeout=5) as r:
    status = json.loads(r.read())
    print(f"\n=== 最终状态 ===")
    print(f"RS485连接: {status['rs485Connected']}")
    print(f"当前区域:   {status['stageStatus']['currentZone']}")
    fans = status['fanStatuses']
    print(f"\n8台风机风速 (%):  " + "  ".join(f"F{f['id']}={f['speedPercent']:3d}%" for f in fans))
    print(f"8台风机方向 (°):  " + "  ".join(f"F{f['id']}={round(f['blowDirectionDegrees']):4d}°" for f in fans))
