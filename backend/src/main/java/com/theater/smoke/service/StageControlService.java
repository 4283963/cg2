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
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class StageControlService {

    private static final Logger log = LoggerFactory.getLogger(StageControlService.class);

    private final WindCalculationService windCalculationService;
    private final RS485Gateway rs485Gateway;
    private final StageLayout stageLayout;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<ActiveControlState> currentState;

    public StageControlService(WindCalculationService windCalculationService, RS485Gateway rs485Gateway) {
        this.windCalculationService = windCalculationService;
        this.rs485Gateway = rs485Gateway;
        this.stageLayout = new StageLayout();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "stage-control-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.currentState = new AtomicReference<>();
    }

    @PostConstruct
    public void initialize() {
        rs485Gateway.connect();
    }

    public SmokeControlResponse applySmokeControl(SmokeControlRequest request) {
        try {
            WindCalculationService.WindCalculationResult result =
                    windCalculationService.calculateWindPattern(request, stageLayout);

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

            syncHardwareState(fans, machines);
            saveState(request, fans, machines);

            if (request.getDurationSeconds() > 0) {
                scheduleAutoStop(request.getDurationSeconds());
            }

            return SmokeControlResponse.success(fans, machines, result.description);
        } catch (Exception e) {
            log.error("应用烟雾控制失败", e);
            return SmokeControlResponse.error("系统错误: " + e.getMessage());
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
            rs485Gateway.sendFanCommand(target);
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
            rs485Gateway.sendSmokeMachineCommand(target);
            updateMachineInLayout(target);
            return SmokeControlResponse.success(getCurrentFanSnapshot(), machines, "手动调节干冰机: " + target.getName());
        } catch (Exception e) {
            log.error("手动调节干冰机失败", e);
            return SmokeControlResponse.error("调节失败: " + e.getMessage());
        }
    }

    public SmokeControlResponse emergencyStop() {
        try {
            Fan[] fans = stageLayout.getFans();
            for (Fan fan : fans) {
                fan.setSpeedPercent(0);
                fan.setActive(false);
                fan.setBlowDirectionDegrees(0);
                rs485Gateway.sendFanCommand(fan);
            }
            SmokeMachine[] machines = stageLayout.getSmokeMachines();
            for (SmokeMachine machine : machines) {
                machine.setOutputPercent(0);
                machine.setActive(false);
                rs485Gateway.sendSmokeMachineCommand(machine);
            }
            currentState.set(null);
            return SmokeControlResponse.success(fans, machines, "紧急停止所有设备");
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

    private void syncHardwareState(Fan[] fans, SmokeMachine[] machines) {
        for (Fan fan : fans) {
            rs485Gateway.sendFanCommand(fan);
        }
        for (SmokeMachine machine : machines) {
            rs485Gateway.sendSmokeMachineCommand(machine);
        }
    }

    private void saveState(SmokeControlRequest request, Fan[] fans, SmokeMachine[] machines) {
        ActiveControlState state = new ActiveControlState();
        state.request = request;
        state.fans = fans;
        state.machines = machines;
        currentState.set(state);
    }

    private void scheduleAutoStop(int seconds) {
        scheduler.schedule(() -> {
            log.info("定时停止：{}秒已到，关闭所有烟雾输出", seconds);
            SmokeMachine[] machines = stageLayout.getSmokeMachines();
            for (SmokeMachine machine : machines) {
                if (machine.isActive()) {
                    machine.setOutputPercent(0);
                    machine.setActive(false);
                    rs485Gateway.sendSmokeMachineCommand(machine);
                }
            }
            scheduler.schedule(() -> {
                log.info("烟雾已停止60秒，关闭所有风机");
                Fan[] fans = stageLayout.getFans();
                for (Fan fan : fans) {
                    fan.setSpeedPercent(0);
                    fan.setActive(false);
                    rs485Gateway.sendFanCommand(fan);
                }
            }, 60, TimeUnit.SECONDS);
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

    private static class ActiveControlState {
        SmokeControlRequest request;
        Fan[] fans;
        SmokeMachine[] machines;
    }
}
