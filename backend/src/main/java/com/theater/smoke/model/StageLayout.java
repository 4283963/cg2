package com.theater.smoke.model;

public class StageLayout {
    private double widthMeters;
    private double depthMeters;
    private double audienceSafeDistanceMeters;
    private Fan[] fans;
    private SmokeMachine[] smokeMachines;

    public StageLayout() {
        this.widthMeters = 16;
        this.depthMeters = 10;
        this.audienceSafeDistanceMeters = 1;

        this.fans = new Fan[]{
                new Fan(1, "F1-左前角", -7.5, -4.5),
                new Fan(2, "F2-左侧墙中", -7.5, 0),
                new Fan(3, "F3-左后角", -7.5, 4.5),
                new Fan(4, "F4-后墙左中", -3.5, 4.5),
                new Fan(5, "F5-后墙右中", 3.5, 4.5),
                new Fan(6, "F6-右后角", 7.5, 4.5),
                new Fan(7, "F7-右侧墙中", 7.5, 0),
                new Fan(8, "F8-右前角", 7.5, -4.5)
        };

        this.smokeMachines = new SmokeMachine[]{
                new SmokeMachine(1, "SM1-中央机", StageZone.CENTER, 0, 0),
                new SmokeMachine(2, "SM2-左背景机", StageZone.LEFT_BACK, -5, 3),
                new SmokeMachine(3, "SM3-右背景机", StageZone.RIGHT_BACK, 5, 3),
                new SmokeMachine(4, "SM4-前缘机", StageZone.FRONT, 0, -3)
        };
    }

    public double getWidthMeters() { return widthMeters; }
    public double getDepthMeters() { return depthMeters; }
    public double getAudienceSafeDistanceMeters() { return audienceSafeDistanceMeters; }
    public Fan[] getFans() { return fans; }
    public SmokeMachine[] getSmokeMachines() { return smokeMachines; }

    public void setWidthMeters(double widthMeters) { this.widthMeters = widthMeters; }
    public void setDepthMeters(double depthMeters) { this.depthMeters = depthMeters; }
    public void setAudienceSafeDistanceMeters(double v) { this.audienceSafeDistanceMeters = v; }
    public void setFans(Fan[] fans) { this.fans = fans; }
    public void setSmokeMachines(SmokeMachine[] smokeMachines) { this.smokeMachines = smokeMachines; }
}
