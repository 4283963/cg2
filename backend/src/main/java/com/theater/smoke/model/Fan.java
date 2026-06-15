package com.theater.smoke.model;

public class Fan {
    private int id;
    private String name;
    private double positionX;
    private double positionY;
    private int speedPercent;
    private double blowDirectionDegrees;
    private boolean active;

    public Fan(int id, String name, double positionX, double positionY) {
        this.id = id;
        this.name = name;
        this.positionX = positionX;
        this.positionY = positionY;
        this.speedPercent = 0;
        this.blowDirectionDegrees = 0;
        this.active = false;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public double getPositionX() { return positionX; }
    public double getPositionY() { return positionY; }
    public int getSpeedPercent() { return speedPercent; }
    public double getBlowDirectionDegrees() { return blowDirectionDegrees; }
    public boolean isActive() { return active; }

    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPositionX(double positionX) { this.positionX = positionX; }
    public void setPositionY(double positionY) { this.positionY = positionY; }
    public void setSpeedPercent(int speedPercent) { this.speedPercent = speedPercent; }
    public void setBlowDirectionDegrees(double blowDirectionDegrees) { this.blowDirectionDegrees = blowDirectionDegrees; }
    public void setActive(boolean active) { this.active = active; }
}
