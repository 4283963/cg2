package com.theater.smoke.model;

public class SmokeMachine {
    private int id;
    private String name;
    private StageZone assignedZone;
    private double positionX;
    private double positionY;
    private int outputPercent;
    private boolean active;
    private boolean warmingUp;

    public SmokeMachine(int id, String name, StageZone assignedZone, double positionX, double positionY) {
        this.id = id;
        this.name = name;
        this.assignedZone = assignedZone;
        this.positionX = positionX;
        this.positionY = positionY;
        this.outputPercent = 0;
        this.active = false;
        this.warmingUp = false;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public StageZone getAssignedZone() { return assignedZone; }
    public double getPositionX() { return positionX; }
    public double getPositionY() { return positionY; }
    public int getOutputPercent() { return outputPercent; }
    public boolean isActive() { return active; }
    public boolean isWarmingUp() { return warmingUp; }

    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setAssignedZone(StageZone assignedZone) { this.assignedZone = assignedZone; }
    public void setPositionX(double positionX) { this.positionX = positionX; }
    public void setPositionY(double positionY) { this.positionY = positionY; }
    public void setOutputPercent(int outputPercent) { this.outputPercent = outputPercent; }
    public void setActive(boolean active) { this.active = active; }
    public void setWarmingUp(boolean warmingUp) { this.warmingUp = warmingUp; }
}
