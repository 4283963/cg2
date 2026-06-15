package com.theater.smoke.model;

public enum StageZone {
    CENTER("舞台中央", 0, 0),
    LEFT_BACK("左侧背景", -5, 3),
    RIGHT_BACK("右侧背景", 5, 3),
    FRONT("舞台前缘", 0, -3),
    BACK("舞台后缘", 0, 4),
    LEFT_FRONT("左前区域", -4, -3),
    RIGHT_FRONT("右前区域", 4, -3),
    FULL_STAGE("整个舞台", 0, 0);

    private final String displayName;
    private final double centerX;
    private final double centerY;

    StageZone(String displayName, double centerX, double centerY) {
        this.displayName = displayName;
        this.centerX = centerX;
        this.centerY = centerY;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }
}
