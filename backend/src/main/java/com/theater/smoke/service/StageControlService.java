package com.theater.smoke.service;

import com.theater.smoke.dto.ManualFanAdjustRequest;
import com.theater.smoke.dto.ManualSmokeAdjustRequest;
import com.theater.smoke.dto.SmokeControlRequest;
import com.theater.smoke.dto.SmokeControlResponse;
import com.theater.smoke.dto.SystemStatusResponse;
import com.theater.smoke.hardware.RS485Gateway;
import com.theater.smoke.model.Fan;
import com.theater.smoke.model.SmokeMachine;
import com.theater.smoke.model.StageLayout;
import com.theater.smoke.model.StageZone;
import com.theater.smoke.model.WindDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class StageControlService {

    private static final Logger log = LoggerFactory.getLogger(StageControlService.class);
    private static final long DEBOUNCE_MS = 250L;
    private static final long AUTO_STOP_SMOKE_DELAY_S = 60L;

    private static final long RHYTHM_TICK_MS = 100L;
    private static final double RHYTHM_MAX_WOBBLE = 0.10;
    private static final double RHYTHM_HZ = 1.3;
    private static final int RHYTHM_MAX_DB = 120;

    private final WindCalculationService windCalculationService;
    private final RS485Gateway rs485Gateway;
    private final StageLayout stageLayout;
    private final ScheduledExecutorService scheduler;

    private final AtomicReference<PendingApply> pendingApplyRef = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> debounceFutureRef = new AtomicReference<>();
    private final AtomicLong applyGeneration = new AtomicLong(0);
    private final AtomicReference<ActiveControlState> currentState = new AtomicReference<>();

    private final AtomicBoolean musicModeEnabled = new AtomicBoolean(false);
    private final AtomicInteger currentDecibels = new AtomicInteger(0);
    private final AtomicReference<ScheduledFuture<?>> rhythmFutureRef = new AtomicReference<>();
    private final AtomicLong rhythmStartNanos = new AtomicLong(0);

    public StageControlService(WindCalculationService windCalculationService, RS485Gateway rs485Gateway) {
        this.windCalculationService = windCalculationService;
        this.rs485Gateway = rs485Gateway;
        this.stageLayout = new StageLayout();
        this.scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "stage-control-scheduler");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((t1, e) -> log.error("调度线程异常", e));
            return t;
        });
    }

    public SmokeControlResponse applySmokeControl(SmokeControlRequest request) {
        final long generation = applyGeneration.incrementAndGet();

        WindCalculationService.WindCalculationResult calc;
        Fan[] fans;
        SmokeMachine[] machines;
        try {
            calc = windCalculationService.calculateWindPattern(request, stageLayout);
            fans = applyFanSettings(calc);
            machines = applySmokeSettings(calc);
        } catch (Exception e) {
            log.error("烟雾参数计算失败", e);
            return SmokeControlResponse.error("参数计算错误: " + e.getMessage());
        }

        PendingApply pending = new PendingApply(generation, request, fans, machines, calc.description);
        pendingApplyRef.set(pending);

        cancelPreviousDebounce();
        ScheduledFuture<?> future = scheduler.schedule(() -> flushDebouncedApply(generation),
                DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        debounceFutureRef.set(future);

        saveState(request, fans, machines);
        log.info("[Debounce] 第{}次请求入队 目标={} 方向={} 抖动窗口={}ms，若期间有新请求将合并",
                generation, request.getTargetZone(), request.getSmokeFlowDirection(), DEBOUNCE_MS);

        return SmokeControlResponse.success(fans, machines,
                calc.description + String.format(" (已入队，%dms后执行，快速切换将自动合并)", DEBOUNCE_MS));
    }

    private void flushDebouncedApply(long expectedGeneration) {
        PendingApply pending = pendingApplyRef.get();
        if (pending == null) return;
        if (pending.generation != expectedGeneration) {
            log.debug("[Debounce] 跳过第{}次刷新，已有更新的第{}次请求",
                    expectedGeneration, pending.generation);
            return;
        }
        long gen = pending.generation;
        log.info("[Debounce] 刷新窗口到，执行第{}次请求 目标={}",
                gen, pending.request.getTargetZone());

        syncHardwareStateBatch(pending.fans, pending.machines)
                .whenComplete((successCount, throwable) -> {
                    if (throwable != null) {
                        log.error("[Debounce] 第{}次批处理异常", gen, throwable);
                    } else {
                        log.info("[Debounce] 第{}次批处理完成，成功下发{}条指令", gen, successCount);
                        if (musicModeEnabled.get()) {
                            startRhythmLoopIfNeeded();
                        }
                    }
                });

        if (pending.request.getDurationSeconds() > 0) {
            scheduleAutoStop(pending.request.getDurationSeconds());
        }
    }

    private void cancelPreviousDebounce() {
        ScheduledFuture<?> prev = debounceFutureRef.getAndSet(null);
        if (prev != null && !prev.isDone()) {
            prev.cancel(false);
            log.debug("[Debounce] 取消前一次未执行的调度");
        }
    }

    public SmokeControlResponse manualAdjustFan(ManualFanAdjustRequest request) {
        try {
            Fan[] fans = getCurrentFanSnapshot();
            Fan target = null;
            for (Fan fan : fans) {
                if (fan.getId() == request.getFanId()) {
                    target = fan;
                    break;
                }
            }
            if (target == null) {
                return SmokeControlResponse.error("找不到风机ID: " + request.getFanId());
            }
            if (request.getSpeedPercent() != null) {
                target.setSpeedPercent(Math.max(0, Math.min(100, request.getSpeedPercent())));
                target.setActive(target.getSpeedPercent() > 3);
            }
            if (request.getBlowDirectionDegrees() != null) {
                target.setBlowDirectionDegrees(request.getBlowDirectionDegrees());
            }
            if (request.getActive() != null) {
                target.setActive(request.getActive());
                if (!target.isActive()) {
                    target.setSpeedPercent(0);
                }
            }
            rs485Gateway.sendFanCommandAsync(target);
            updateFanInLayout(target);
            return SmokeControlResponse.success(fans, getCurrentMachineSnapshot(), "手动调节风机: " + target.getName());
        } catch (Exception e) {
            log.error("手动调节风机失败", e);
            return SmokeControlResponse.error("调节失败: " + e.getMessage());
        }
    }

    public SmokeControlResponse manualAdjustSmokeMachine(ManualSmokeAdjustRequest request) {
        try {
            SmokeMachine[] machines = getCurrentMachineSnapshot();
            SmokeMachine target = null;
            for (SmokeMachine machine : machines) {
                if (machine.getId() == request.getMachineId()) {
                    target = machine;
                    break;
                }
            }
            if (target == null) {
                return SmokeControlResponse.error("找不到干冰机ID: " + request.getMachineId());
            }
            if (request.getOutputPercent() != null) {
                target.setOutputPercent(Math.max(0, Math.min(100, request.getOutputPercent())));
                target.setActive(target.getOutputPercent() > 5);
            }
            if (request.getActive() != null) {
                target.setActive(request.getActive());
                if (!target.isActive()) {
                    target.setOutputPercent(0);
                }
            }
            rs485Gateway.sendSmokeMachineCommandAsync(target);
            updateMachineInLayout(target);
            return SmokeControlResponse.success(getCurrentFanSnapshot(), machines, "手动调节干冰机: " + target.getName());
        } catch (Exception e) {
            log.error("手动调节干冰机失败", e);
            return SmokeControlResponse.error("调节失败: " + e.getMessage());
        }
    }

    public SmokeControlResponse emergencyStop() {
        try {
            cancelPreviousDebounce();
            pendingApplyRef.set(null);
            stopRhythmLoop();
            musicModeEnabled.set(false);
            currentDecibels.set(0);

            Fan[] fans = stageLayout.getFans();
            for (Fan fan : fans) {
                fan.setSpeedPercent(0);
                fan.setActive(false);
                fan.setBlowDirectionDegrees(0);
            }
            SmokeMachine[] machines = stageLayout.getSmokeMachines();
            for (SmokeMachine machine : machines) {
                machine.setOutputPercent(0);
                machine.setActive(false);
            }
            currentState.set(null);

            syncHardwareStateBatch(fans, machines).whenComplete((n, t) -> {
                if (t != null) {
                    log.error("[紧急停止] 批处理异常", t);
                } else {
                    log.info("[紧急停止] 成功下发{}条零值指令", n);
                }
            });

            return SmokeControlResponse.success(fans, machines, "紧急停止：所有设备零值指令已批量下发，音乐律动已强制关闭");
        } catch (Exception e) {
            log.error("紧急停止失败", e);
            return SmokeControlResponse.error("停止失败: " + e.getMessage());
        }
    }

    public SystemStatusResponse getSystemStatus() {
        SystemStatusResponse resp = new SystemStatusResponse();
        resp.setSystemOnline(true);
        resp.setRs485Connected(rs485Gateway.isConnected());
        resp.setFanStatuses(getCurrentFanSnapshot());
        resp.setSmokeMachineStatuses(getCurrentMachineSnapshot());

        ActiveControlState state = currentState.get();
        SystemStatusResponse.StageStatus stage = new SystemStatusResponse.StageStatus();
        if (state != null) {
            stage.setCurrentZone(state.request.getTargetZone().getDisplayName());
            stage.setCurrentFlowDirection(state.request.getSmokeFlowDirection().getDisplayName());
            stage.setCurrentDensity(state.request.getSmokeDensityPercent());
            stage.setAudienceProtected(state.request.isAudienceProtectionEnabled());
        } else {
            stage.setCurrentZone("无");
            stage.setCurrentFlowDirection(WindDirection.STATIC.getDisplayName());
            stage.setCurrentDensity(0);
            stage.setAudienceProtected(true);
        }
        resp.setStageStatus(stage);
        return resp;
    }

    public StageLayout getStageLayout() {
        return stageLayout;
    }

    public MusicStatus setMusicModeEnabled(boolean enabled) {
        musicModeEnabled.set(enabled);
        if (enabled) {
            rhythmStartNanos.set(System.nanoTime());
            startRhythmLoopIfNeeded();
            log.info("[MusicRhy] 音乐律动模式已开启，最大风速扰动 ±{}%", (int)(RHYTHM_MAX_WOBBLE * 100));
        } else {
            stopRhythmLoop();
            restoreBaselineFans();
            log.info("[MusicRhy] 音乐律动模式已关闭，所有风机已恢复基准风速");
        }
        return getMusicStatus();
    }

    public MusicStatus reportDecibels(int db) {
        int clamped = Math.max(0, Math.min(RHYTHM_MAX_DB, db));
        currentDecibels.set(clamped);
        if (log.isDebugEnabled()) {
            log.debug("[MusicRhy] 音控台上报分贝 {}dB (归一化 {}%)", clamped, String.format("%.0f", clamped * 100.0 / RHYTHM_MAX_DB));
        }
        return getMusicStatus();
    }

    public MusicStatus getMusicStatus() {
        MusicStatus s = new MusicStatus();
        s.setEnabled(musicModeEnabled.get());
        s.setCurrentDecibels(currentDecibels.get());
        s.setDecibelFactor(currentDecibels.get() * 1.0 / RHYTHM_MAX_DB);
        s.setMaxWobblePercent((int)(RHYTHM_MAX_WOBBLE * 100));
        s.setRhythmHz(RHYTHM_HZ);
        s.setTickIntervalMs(RHYTHM_TICK_MS);
        ScheduledFuture<?> f = rhythmFutureRef.get();
        s.setLoopRunning(f != null && !f.isDone());
        return s;
    }

    private void startRhythmLoopIfNeeded() {
        if (!musicModeEnabled.get()) return;
        ActiveControlState state = currentState.get();
        if (state == null) {
            log.warn("[MusicRhy] 无有效的舞台控制状态，律动循环暂不启动（请先应用一次烟雾控制）");
            return;
        }
        ScheduledFuture<?> prev = rhythmFutureRef.get();
        if (prev != null && !prev.isDone()) return;
        rhythmStartNanos.set(System.nanoTime());
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                this::tickRhythm, RHYTHM_TICK_MS, RHYTHM_TICK_MS, TimeUnit.MILLISECONDS);
        rhythmFutureRef.set(future);
        log.info("[MusicRhy] 律动循环已启动 tick={}ms 频率={}Hz", RHYTHM_TICK_MS, RHYTHM_HZ);
    }

    private void stopRhythmLoop() {
        ScheduledFuture<?> prev = rhythmFutureRef.getAndSet(null);
        if (prev != null && !prev.isDone()) {
            prev.cancel(false);
            log.info("[MusicRhy] 律动循环已停止");
        }
    }

    private void restoreBaselineFans() {
        ActiveControlState state = currentState.get();
        if (state == null) return;
        Fan[] baseline = state.baselineFans;
        if (baseline == null) return;
        for (int i = 0; i < baseline.length; i++) {
            Fan target = state.fans[i];
            target.setSpeedPercent(baseline[i].getSpeedPercent());
            target.setBlowDirectionDegrees(baseline[i].getBlowDirectionDegrees());
            target.setActive(baseline[i].isActive());
            updateFanInLayout(target);
        }
        syncHardwareStateBatch(state.fans, state.machines);
    }

    private void tickRhythm() {
        try {
            ActiveControlState state = currentState.get();
            if (state == null || state.baselineFans == null) {
                stopRhythmLoop();
                return;
            }
            if (!musicModeEnabled.get()) {
                stopRhythmLoop();
                restoreBaselineFans();
                return;
            }

            double dbFactor = currentDecibels.get() * 1.0 / RHYTHM_MAX_DB;
            if (dbFactor <= 0.001) {
                return;
            }

            long elapsedNs = System.nanoTime() - rhythmStartNanos.get();
            double tSec = elapsedNs / 1_000_000_000.0;
            double globalPhase = tSec * 2 * Math.PI * RHYTHM_HZ;

            double amplitude = RHYTHM_MAX_WOBBLE * dbFactor;
            int fanCount = state.baselineFans.length;
            Fan[] perturbedFans = new Fan[fanCount];
            for (int i = 0; i < fanCount; i++) {
                Fan base = state.baselineFans[i];
                Fan perturbed = new Fan(base.getId(), base.getName(), base.getPositionX(), base.getPositionY());
                perturbed.setActive(base.isActive());
                perturbed.setBlowDirectionDegrees(base.getBlowDirectionDegrees());
                double phaseOffset = i * (2 * Math.PI / fanCount) + dbFactor * 2.7;
                double sinVal = Math.sin(globalPhase + phaseOffset);
                double speedFactor = 1.0 + sinVal * amplitude;
                int newSpeed = (int) Math.round(base.getSpeedPercent() * speedFactor);
                newSpeed = Math.max(0, Math.min(100, newSpeed));
                perturbed.setSpeedPercent(newSpeed);
                perturbedFans[i] = perturbed;
                updateFanInLayout(perturbed);
            }

            SmokeMachine[] perturbedMachines = state.baselineMachines;
            if (perturbedMachines != null && dbFactor > 0.2) {
                SmokeMachine[] copy = new SmokeMachine[perturbedMachines.length];
                for (int i = 0; i < perturbedMachines.length; i++) {
                    SmokeMachine base = perturbedMachines[i];
                    SmokeMachine d = new SmokeMachine(base.getId(), base.getName(), base.getAssignedZone(), base.getPositionX(), base.getPositionY());
                    d.setActive(base.isActive());
                    double machinePhase = globalPhase * 0.5 + i * Math.PI / 3;
                    double sinV = Math.sin(machinePhase);
                    int newOut = (int) Math.round(base.getOutputPercent() * (1 + sinV * 0.05 * dbFactor));
                    newOut = Math.max(0, Math.min(100, newOut));
                    d.setOutputPercent(newOut);
                    copy[i] = d;
                    updateMachineInLayout(d);
                }
                perturbedMachines = copy;
            }

            syncHardwareStateBatch(perturbedFans, perturbedMachines);
        } catch (Exception e) {
            log.warn("[MusicRhy] tick异常，停止循环: {}", e.getMessage());
            stopRhythmLoop();
        }
    }

    public static class MusicStatus {
        private boolean enabled;
        private int currentDecibels;
        private double decibelFactor;
        private int maxWobblePercent;
        private double rhythmHz;
        private long tickIntervalMs;
        private boolean loopRunning;

        public boolean isEnabled() { return enabled; }
        public int getCurrentDecibels() { return currentDecibels; }
        public double getDecibelFactor() { return decibelFactor; }
        public int getMaxWobblePercent() { return maxWobblePercent; }
        public double getRhythmHz() { return rhythmHz; }
        public long getTickIntervalMs() { return tickIntervalMs; }
        public boolean isLoopRunning() { return loopRunning; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setCurrentDecibels(int d) { this.currentDecibels = d; }
        public void setDecibelFactor(double d) { this.decibelFactor = d; }
        public void setMaxWobblePercent(int p) { this.maxWobblePercent = p; }
        public void setRhythmHz(double h) { this.rhythmHz = h; }
        public void setTickIntervalMs(long t) { this.tickIntervalMs = t; }
        public void setLoopRunning(boolean r) { this.loopRunning = r; }
    }

    private Fan[] applyFanSettings(WindCalculationService.WindCalculationResult result) {
        Fan[] fans = Arrays.copyOf(stageLayout.getFans(), stageLayout.getFans().length);
        for (WindCalculationService.FanSetting fs : result.fanSettings) {
            for (Fan fan : fans) {
                if (fan.getId() == fs.fanId) {
                    fan.setSpeedPercent(fs.speedPercent);
                    fan.setBlowDirectionDegrees(fs.directionDegrees);
                    fan.setActive(fs.active);
                    break;
                }
            }
        }
        for (Fan fan : fans) {
            updateFanInLayout(fan);
        }
        return fans;
    }

    private SmokeMachine[] applySmokeSettings(WindCalculationService.WindCalculationResult result) {
        SmokeMachine[] machines = Arrays.copyOf(stageLayout.getSmokeMachines(), stageLayout.getSmokeMachines().length);
        for (WindCalculationService.SmokeMachineSetting ms : result.smokeMachineSettings) {
            for (SmokeMachine machine : machines) {
                if (machine.getId() == ms.machineId) {
                    machine.setOutputPercent(ms.outputPercent);
                    machine.setActive(ms.active);
                    break;
                }
            }
        }
        for (SmokeMachine m : machines) {
            updateMachineInLayout(m);
        }
        return machines;
    }

    private CompletableFuture<Integer> syncHardwareStateBatch(Fan[] fans, SmokeMachine[] machines) {
        return rs485Gateway.submitBatch(fans, machines);
    }

    private void saveState(SmokeControlRequest request, Fan[] fans, SmokeMachine[] machines) {
        ActiveControlState state = new ActiveControlState();
        state.request = request;
        state.fans = fans;
        state.machines = machines;
        state.baselineFans = deepCopyFans(fans);
        state.baselineMachines = deepCopyMachines(machines);
        currentState.set(state);
    }

    private Fan[] deepCopyFans(Fan[] src) {
        Fan[] dst = new Fan[src.length];
        for (int i = 0; i < src.length; i++) {
            Fan s = src[i];
            Fan d = new Fan(s.getId(), s.getName(), s.getPositionX(), s.getPositionY());
            d.setSpeedPercent(s.getSpeedPercent());
            d.setBlowDirectionDegrees(s.getBlowDirectionDegrees());
            d.setActive(s.isActive());
            dst[i] = d;
        }
        return dst;
    }

    private SmokeMachine[] deepCopyMachines(SmokeMachine[] src) {
        SmokeMachine[] dst = new SmokeMachine[src.length];
        for (int i = 0; i < src.length; i++) {
            SmokeMachine s = src[i];
            SmokeMachine d = new SmokeMachine(s.getId(), s.getName(), s.getAssignedZone(), s.getPositionX(), s.getPositionY());
            d.setOutputPercent(s.getOutputPercent());
            d.setActive(s.isActive());
            dst[i] = d;
        }
        return dst;
    }

    private void scheduleAutoStop(int seconds) {
        scheduler.schedule(() -> {
            log.info("定时停止：{}秒已到，关闭所有烟雾输出（保留风机60秒吹散）", seconds);
            SmokeMachine[] machines = stageLayout.getSmokeMachines();
            Fan[] fansSnapshot = getCurrentFanSnapshot();
            for (SmokeMachine machine : machines) {
                if (machine.isActive()) {
                    machine.setOutputPercent(0);
                    machine.setActive(false);
                    updateMachineInLayout(machine);
                }
            }
            rs485Gateway.submitBatch(fansSnapshot, machines);

            scheduler.schedule(() -> {
                log.info("烟雾已停止{}秒，关闭所有风机", AUTO_STOP_SMOKE_DELAY_S);
                Fan[] fans = stageLayout.getFans();
                for (Fan fan : fans) {
                    fan.setSpeedPercent(0);
                    fan.setActive(false);
                    updateFanInLayout(fan);
                }
                SmokeMachine[] m2 = getCurrentMachineSnapshot();
                rs485Gateway.submitBatch(fans, m2);
            }, AUTO_STOP_SMOKE_DELAY_S, TimeUnit.SECONDS);
        }, seconds, TimeUnit.SECONDS);
    }

    private Fan[] getCurrentFanSnapshot() {
        return Arrays.copyOf(stageLayout.getFans(), stageLayout.getFans().length);
    }

    private SmokeMachine[] getCurrentMachineSnapshot() {
        return Arrays.copyOf(stageLayout.getSmokeMachines(), stageLayout.getSmokeMachines().length);
    }

    private void updateFanInLayout(Fan updated) {
        for (Fan fan : stageLayout.getFans()) {
            if (fan.getId() == updated.getId()) {
                fan.setSpeedPercent(updated.getSpeedPercent());
                fan.setBlowDirectionDegrees(updated.getBlowDirectionDegrees());
                fan.setActive(updated.isActive());
                break;
            }
        }
    }

    private void updateMachineInLayout(SmokeMachine updated) {
        for (SmokeMachine machine : stageLayout.getSmokeMachines()) {
            if (machine.getId() == updated.getId()) {
                machine.setOutputPercent(updated.getOutputPercent());
                machine.setActive(updated.isActive());
                break;
            }
        }
    }

    private static class PendingApply {
        final long generation;
        final SmokeControlRequest request;
        final Fan[] fans;
        final SmokeMachine[] machines;
        final String description;

        PendingApply(long generation, SmokeControlRequest request,
                     Fan[] fans, SmokeMachine[] machines, String description) {
            this.generation = generation;
            this.request = request;
            this.fans = fans;
            this.machines = machines;
            this.description = description;
        }
    }

    private static class ActiveControlState {
        SmokeControlRequest request;
        Fan[] fans;
        SmokeMachine[] machines;
        Fan[] baselineFans;
        SmokeMachine[] baselineMachines;
    }
}
