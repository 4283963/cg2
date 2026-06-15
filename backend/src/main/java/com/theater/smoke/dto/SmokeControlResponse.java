package com.theater.smoke.dto;

import com.theater.smoke.model.Fan;
import com.theater.smoke.model.SmokeMachine;

import java.time.LocalDateTime;

public class SmokeControlResponse {
    private boolean success;
    private String message;
    private Fan[] fanSettings;
    private SmokeMachine[] smokeMachineSettings;
    private LocalDateTime timestamp;
    private String windPatternDescription;

    public SmokeControlResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public static SmokeControlResponse success(Fan[] fans, SmokeMachine[] machines, String description) {
        SmokeControlResponse resp = new SmokeControlResponse();
        resp.success = true;
        resp.message = "控制指令已下发";
        resp.fanSettings = fans;
        resp.smokeMachineSettings = machines;
        resp.windPatternDescription = description;
        return resp;
    }

    public static SmokeControlResponse error(String message) {
        SmokeControlResponse resp = new SmokeControlResponse();
        resp.success = false;
        resp.message = message;
        return resp;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Fan[] getFanSettings() { return fanSettings; }
    public SmokeMachine[] getSmokeMachineSettings() { return smokeMachineSettings; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getWindPatternDescription() { return windPatternDescription; }

    public void setSuccess(boolean success) { this.success = success; }
    public void setMessage(String message) { this.message = message; }
    public void setFanSettings(Fan[] fanSettings) { this.fanSettings = fanSettings; }
    public void setSmokeMachineSettings(SmokeMachine[] smokeMachineSettings) { this.smokeMachineSettings = smokeMachineSettings; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setWindPatternDescription(String windPatternDescription) { this.windPatternDescription = windPatternDescription; }
}
