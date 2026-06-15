package com.theater.smoke.service;

import com.theater.smoke.dto.SmokeControlRequest;
import com.theater.smoke.model.Fan;
import com.theater.smoke.model.SmokeMachine;
import com.theater.smoke.model.StageLayout;
import com.theater.smoke.model.StageZone;
import com.theater.smoke.model.WindDirection;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WindCalculationService {

    private static final double STAGE_HALF_WIDTH = 8.0;
    private static final double STAGE_HALF_DEPTH = 5.0;
    private static final double ZONE_RADIUS_DEFAULT = 3.5;
    private static final double FULL_STAGE_RADIUS = 7.0;
    private static final double AUDIENCE_DIRECTION_Y = -1.0;
    private static final double CONTAINMENT_WEIGHT = 0.8;
    private static final double FLOW_WEIGHT = 0.5;
    private static final double AUDIENCE_PROTECTION_BONUS = 0.4;
    private static final double OPPOSITION_CONTAINMENT_BONUS = 0.25;

    public WindCalculationResult calculateWindPattern(SmokeControlRequest request, StageLayout layout) {
        StageZone targetZone = request.getTargetZone();
        WindDirection flowDir = request.getSmokeFlowDirection();
        int containmentStrength = request.getContainmentStrengthPercent();
        int smokeDensity = request.getSmokeDensityPercent();
        boolean protectAudience = request.isAudienceProtectionEnabled();

        double zoneCenterX = targetZone.getCenterX();
        double zoneCenterY = targetZone.getCenterY();
        double zoneRadius = targetZone == StageZone.FULL_STAGE ? FULL_STAGE_RADIUS : ZONE_RADIUS_DEFAULT;

        List<FanSetting> fanSettings = new ArrayList<>();
        StringBuilder description = new StringBuilder();

        description.append(String.format("目标区域：%s，约束强度：%d%%，",
                targetZone.getDisplayName(), containmentStrength));

        if (flowDir == WindDirection.STATIC) {
            description.append("烟雾静止悬浮模式");
        } else if (flowDir.isCircular()) {
            description.append(String.format("烟雾%s旋转",
                    flowDir == WindDirection.CIRCULATE_CW ? "顺时针" : "逆时针"));
        } else {
            description.append(String.format("烟雾飘动方向：%s", flowDir.getDisplayName()));
        }

        for (Fan fan : layout.getFans()) {
            FanSetting setting = calculateSingleFan(
                    fan, zoneCenterX, zoneCenterY, zoneRadius,
                    flowDir, containmentStrength, protectAudience
            );
            fanSettings.add(setting);
        }

        List<SmokeMachineSetting> machineSettings = calculateSmokeMachines(
                targetZone, smokeDensity, layout
        );

        return new WindCalculationResult(fanSettings, machineSettings, description.toString());
    }

    private FanSetting calculateSingleFan(
            Fan fan, double zoneCX, double zoneCY, double zoneRadius,
            WindDirection flowDir, int containmentStrength, boolean protectAudience
    ) {
        double fanX = fan.getPositionX();
        double fanY = fan.getPositionY();

        double toCenterX = zoneCX - fanX;
        double toCenterY = zoneCY - fanY;
        double distToCenter = Math.sqrt(toCenterX * toCenterX + toCenterY * toCenterY);

        double containmentDirX, containmentDirY;
        if (distToCenter > 0.001) {
            containmentDirX = toCenterX / distToCenter;
            containmentDirY = toCenterY / distToCenter;
        } else {
            containmentDirX = 0;
            containmentDirY = -1;
        }

        double containmentBaseSpeed = containmentStrength / 100.0 * CONTAINMENT_WEIGHT;
        double flowComponentSpeed = containmentStrength / 100.0 * FLOW_WEIGHT;

        double finalDirX = containmentDirX;
        double finalDirY = containmentDirY;
        double finalSpeed = containmentBaseSpeed;

        if (flowDir == WindDirection.STATIC) {
            applyStaticContainment(fan, zoneCX, zoneCY, zoneRadius, containmentStrength);
        } else if (flowDir.isCircular()) {
            double[] circ = calculateCircularFlow(fan, zoneCX, zoneCY, flowDir);
            finalDirX = blend(containmentDirX, circ[0], 0.55, 0.45);
            finalDirY = blend(containmentDirY, circ[1], 0.55, 0.45);
            finalSpeed = containmentBaseSpeed + flowComponentSpeed * 0.7;
        } else {
            double flowVX = flowDir.getVectorX();
            double flowVY = flowDir.getVectorY();

            double dot = containmentDirX * flowVX + containmentDirY * flowVY;
            double oppositionBonus = 0;
            if (dot < 0) {
                oppositionBonus = Math.abs(dot) * OPPOSITION_CONTAINMENT_BONUS * (containmentStrength / 100.0);
            }

            double alignment = Math.max(0, dot);
            finalDirX = blend(containmentDirX, flowVX, 0.6, 0.4 * alignment);
            finalDirY = blend(containmentDirY, flowVY, 0.6, 0.4 * alignment);
            finalSpeed = containmentBaseSpeed + flowComponentSpeed * alignment + oppositionBonus;
        }

        if (protectAudience && fan.getPositionY() < zoneCY - zoneRadius * 0.3) {
            double audienceDot = finalDirY * AUDIENCE_DIRECTION_Y;
            if (audienceDot > 0) {
                finalSpeed += AUDIENCE_PROTECTION_BONUS * (containmentStrength / 100.0);
                double upwardPush = 0.35;
                finalDirY = finalDirY * (1 - upwardPush) + Math.abs(AUDIENCE_DIRECTION_Y) * -1 * upwardPush;
                double len = Math.sqrt(finalDirX * finalDirX + finalDirY * finalDirY);
                if (len > 0) {
                    finalDirX /= len;
                    finalDirY /= len;
                }
            }
        }

        if (distToCenter < zoneRadius * 0.5) {
            finalSpeed *= 0.6;
        }

        int speedPercent = clampSpeed((int) Math.round(finalSpeed * 100));
        double directionDegrees = Math.toDegrees(Math.atan2(finalDirY, finalDirX));
        boolean active = speedPercent > 3;

        return new FanSetting(
                fan.getId(), speedPercent, directionDegrees, active
        );
    }

    private void applyStaticContainment(Fan fan, double zoneCX, double zoneCY,
                                        double zoneRadius, int containmentStrength) {
    }

    private double[] calculateCircularFlow(Fan fan, double zoneCX, double zoneCY, WindDirection dir) {
        double relX = fan.getPositionX() - zoneCX;
        double relY = fan.getPositionY() - zoneCY;
        double tangentX, tangentY;
        if (dir == WindDirection.CIRCULATE_CW) {
            tangentX = relY;
            tangentY = -relX;
        } else {
            tangentX = -relY;
            tangentY = relX;
        }
        double len = Math.sqrt(tangentX * tangentX + tangentY * tangentY);
        if (len > 0.001) {
            return new double[]{tangentX / len, tangentY / len};
        }
        return new double[]{0, 0};
    }

    private List<SmokeMachineSetting> calculateSmokeMachines(
            StageZone targetZone, int density, StageLayout layout
    ) {
        List<SmokeMachineSetting> settings = new ArrayList<>();

        for (SmokeMachine machine : layout.getSmokeMachines()) {
            int output;
            boolean active;

            if (targetZone == StageZone.FULL_STAGE) {
                output = density;
                active = true;
            } else {
                double dist = Math.sqrt(
                        Math.pow(machine.getPositionX() - targetZone.getCenterX(), 2) +
                        Math.pow(machine.getPositionY() - targetZone.getCenterY(), 2)
                );
                double maxDist = 6.0;
                double falloff = Math.max(0, 1 - dist / maxDist);
                output = (int) Math.round(density * falloff);
                active = output >= 8;
                output = Math.max(0, Math.min(100, output));
            }

            settings.add(new SmokeMachineSetting(machine.getId(), output, active));
        }
        return settings;
    }

    private double blend(double a, double b, double wa, double wb) {
        double sum = wa + wb;
        if (sum <= 0) return a;
        return (a * wa + b * wb) / sum;
    }

    private int clampSpeed(int speed) {
        return Math.max(0, Math.min(100, speed));
    }

    public static class FanSetting {
        public final int fanId;
        public final int speedPercent;
        public final double directionDegrees;
        public final boolean active;

        public FanSetting(int fanId, int speedPercent, double directionDegrees, boolean active) {
            this.fanId = fanId;
            this.speedPercent = speedPercent;
            this.directionDegrees = directionDegrees;
            this.active = active;
        }
    }

    public static class SmokeMachineSetting {
        public final int machineId;
        public final int outputPercent;
        public final boolean active;

        public SmokeMachineSetting(int machineId, int outputPercent, boolean active) {
            this.machineId = machineId;
            this.outputPercent = outputPercent;
            this.active = active;
        }
    }

    public static class WindCalculationResult {
        public final List<FanSetting> fanSettings;
        public final List<SmokeMachineSetting> smokeMachineSettings;
        public final String description;

        public WindCalculationResult(List<FanSetting> fanSettings,
                                     List<SmokeMachineSetting> smokeMachineSettings,
                                     String description) {
            this.fanSettings = fanSettings;
            this.smokeMachineSettings = smokeMachineSettings;
            this.description = description;
        }
    }
}
