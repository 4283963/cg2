package com.theater.smoke.dto;

import com.theater.smoke.model.StageZone;
import com.theater.smoke.model.WindDirection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class SmokeControlRequest {

    @NotNull(message = "目标区域不能为空")
    private StageZone targetZone;

    private WindDirection smokeFlowDirection = WindDirection.STATIC;

    @Min(value = 0, message = "烟雾浓度不能小于0%")
    @Max(value = 100, message = "烟雾浓度不能大于100%")
    private Integer smokeDensityPercent = 50;

    @Min(value = 0, message = "约束强度不能小于0%")
    @Max(value = 100, message = "约束强度不能大于100%")
    private Integer containmentStrengthPercent = 80;

    private Boolean audienceProtectionEnabled = true;

    private Integer durationSeconds = 0;

    public StageZone getTargetZone() { return targetZone; }
    public WindDirection getSmokeFlowDirection() { return smokeFlowDirection; }
    public int getSmokeDensityPercent() { return smokeDensityPercent != null ? smokeDensityPercent : 50; }
    public int getContainmentStrengthPercent() { return containmentStrengthPercent != null ? containmentStrengthPercent : 80; }
    public boolean isAudienceProtectionEnabled() { return audienceProtectionEnabled != null ? audienceProtectionEnabled : true; }
    public int getDurationSeconds() { return durationSeconds != null ? durationSeconds : 0; }

    public void setTargetZone(StageZone targetZone) { this.targetZone = targetZone; }
    public void setSmokeFlowDirection(WindDirection smokeFlowDirection) { this.smokeFlowDirection = smokeFlowDirection; }
    public void setSmokeDensityPercent(Integer smokeDensityPercent) { this.smokeDensityPercent = smokeDensityPercent; }
    public void setContainmentStrengthPercent(Integer containmentStrengthPercent) { this.containmentStrengthPercent = containmentStrengthPercent; }
    public void setAudienceProtectionEnabled(Boolean audienceProtectionEnabled) { this.audienceProtectionEnabled = audienceProtectionEnabled; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
}
