package com.theater.smoke.dto;

public class ManualSmokeAdjustRequest {
    private Integer machineId;
    private Integer outputPercent;
    private Boolean active;

    public int getMachineId() { return machineId != null ? machineId : 0; }
    public Integer getOutputPercent() { return outputPercent; }
    public Boolean getActive() { return active; }

    public void setMachineId(Integer machineId) { this.machineId = machineId; }
    public void setOutputPercent(Integer outputPercent) { this.outputPercent = outputPercent; }
    public void setActive(Boolean active) { this.active = active; }
}
