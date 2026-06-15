package com.theater.smoke.dto;

import com.theater.smoke.model.SmokeMachine;

public class SystemStatusResponse {
    private boolean systemOnline;
    private boolean rs485Connected;
    private StageStatus stageStatus;
    private com.theater.smoke.model.Fan[] fanStatuses;
    private SmokeMachine[] smokeMachineStatuses;

    public static class StageStatus {
        private String currentZone;
        private String currentFlowDirection;
        private int currentDensity;
        private boolean audienceProtected;

        public String getCurrentZone() { return currentZone; }
        public String getCurrentFlowDirection() { return currentFlowDirection; }
        public int getCurrentDensity() { return currentDensity; }
        public boolean isAudienceProtected() { return audienceProtected; }

        public void setCurrentZone(String currentZone) { this.currentZone = currentZone; }
        public void setCurrentFlowDirection(String d) { this.currentFlowDirection = d; }
        public void setCurrentDensity(int currentDensity) { this.currentDensity = currentDensity; }
        public void setAudienceProtected(boolean audienceProtected) { this.audienceProtected = audienceProtected; }
    }

    public boolean isSystemOnline() { return systemOnline; }
    public boolean isRs485Connected() { return rs485Connected; }
    public StageStatus getStageStatus() { return stageStatus; }
    public com.theater.smoke.model.Fan[] getFanStatuses() { return fanStatuses; }
    public SmokeMachine[] getSmokeMachineStatuses() { return smokeMachineStatuses; }

    public void setSystemOnline(boolean systemOnline) { this.systemOnline = systemOnline; }
    public void setRs485Connected(boolean rs485Connected) { this.rs485Connected = rs485Connected; }
    public void setStageStatus(StageStatus stageStatus) { this.stageStatus = stageStatus; }
    public void setFanStatuses(com.theater.smoke.model.Fan[] fanStatuses) { this.fanStatuses = fanStatuses; }
    public void setSmokeMachineStatuses(SmokeMachine[] smokeMachineStatuses) { this.smokeMachineStatuses = smokeMachineStatuses; }
}
