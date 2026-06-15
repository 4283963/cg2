package com.theater.smoke.controller;

import com.theater.smoke.dto.ManualFanAdjustRequest;
import com.theater.smoke.dto.ManualSmokeAdjustRequest;
import com.theater.smoke.dto.SmokeControlRequest;
import com.theater.smoke.dto.SmokeControlResponse;
import com.theater.smoke.dto.SystemStatusResponse;
import com.theater.smoke.model.StageLayout;
import com.theater.smoke.model.StageZone;
import com.theater.smoke.model.WindDirection;
import com.theater.smoke.service.StageControlService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/stage")
public class StageController {

    private final StageControlService controlService;

    public StageController(StageControlService controlService) {
        this.controlService = controlService;
    }

    @PostMapping("/smoke/apply")
    public ResponseEntity<SmokeControlResponse> applySmokeControl(
            @Valid @RequestBody SmokeControlRequest request) {
        SmokeControlResponse response = controlService.applySmokeControl(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/fan/adjust")
    public ResponseEntity<SmokeControlResponse> adjustFan(
            @RequestBody ManualFanAdjustRequest request) {
        SmokeControlResponse response = controlService.manualAdjustFan(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/smoke/adjust")
    public ResponseEntity<SmokeControlResponse> adjustSmokeMachine(
            @RequestBody ManualSmokeAdjustRequest request) {
        SmokeControlResponse response = controlService.manualAdjustSmokeMachine(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/emergency-stop")
    public ResponseEntity<SmokeControlResponse> emergencyStop() {
        SmokeControlResponse response = controlService.emergencyStop();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<SystemStatusResponse> getStatus() {
        return ResponseEntity.ok(controlService.getSystemStatus());
    }

    @GetMapping("/layout")
    public ResponseEntity<StageLayout> getLayout() {
        return ResponseEntity.ok(controlService.getStageLayout());
    }

    @GetMapping("/zones")
    public ResponseEntity<List<Map<String, Object>>> getZones() {
        List<Map<String, Object>> zones = Arrays.stream(StageZone.values())
                .map(zone -> Map.<String, Object>of(
                        "name", zone.name(),
                        "displayName", zone.getDisplayName(),
                        "centerX", zone.getCenterX(),
                        "centerY", zone.getCenterY()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(zones);
    }

    @GetMapping("/directions")
    public ResponseEntity<List<Map<String, Object>>> getWindDirections() {
        List<Map<String, Object>> directions = Arrays.stream(WindDirection.values())
                .map(dir -> Map.<String, Object>of(
                        "name", dir.name(),
                        "displayName", dir.getDisplayName(),
                        "vectorX", dir.getVectorX(),
                        "vectorY", dir.getVectorY(),
                        "circular", dir.isCircular()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(directions);
    }

    @PostMapping("/music/config")
    public ResponseEntity<StageControlService.MusicStatus> configureMusicMode(
            @RequestBody Map<String, Object> body) {
        Boolean enabled = (Boolean) body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().build();
        }
        StageControlService.MusicStatus status = controlService.setMusicModeEnabled(enabled);
        Number initDb = (Number) body.get("initialDecibels");
        if (initDb != null) {
            status = controlService.reportDecibels(initDb.intValue());
        }
        return ResponseEntity.ok(status);
    }

    @PostMapping("/music/beat")
    public ResponseEntity<StageControlService.MusicStatus> reportMusicBeat(
            @RequestBody Map<String, Object> body) {
        Number db = (Number) body.get("decibels");
        if (db == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(controlService.reportDecibels(db.intValue()));
    }

    @GetMapping("/music/status")
    public ResponseEntity<StageControlService.MusicStatus> getMusicStatus() {
        return ResponseEntity.ok(controlService.getMusicStatus());
    }
}
