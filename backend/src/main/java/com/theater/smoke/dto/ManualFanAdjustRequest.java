package com.theater.smoke.dto;

public class ManualFanAdjustRequest {
    private Integer fanId;
    private Integer speedPercent;
    private Double blowDirectionDegrees;
    private Boolean active;

    public int getFanId() { return fanId != null ? fanId : 0; }
    public Integer getSpeedPercent() { return speedPercent; }
    public Double getBlowDirectionDegrees() { return blowDirectionDegrees; }
    public Boolean getActive() { return active; }

    public void setFanId(Integer fanId) { this.fanId = fanId; }
    public void setSpeedPercent(Integer speedPercent) { this.speedPercent = speedPercent; }
    public void setBlowDirectionDegrees(Double blowDirectionDegrees) { this.blowDirectionDegrees = blowDirectionDegrees; }
    public void setActive(Boolean active) { this.active = active; }
}
