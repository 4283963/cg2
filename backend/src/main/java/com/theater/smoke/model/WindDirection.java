package com.theater.smoke.model;

public enum WindDirection {
    N("向北", 0, 1),
    NE("向东北", 0.707, 0.707),
    E("向东", 1, 0),
    SE("向东南", 0.707, -0.707),
    S("向南", 0, -1),
    SW("向西南", -0.707, -0.707),
    W("向西", -1, 0),
    NW("向西北", -0.707, 0.707),
    STATIC("静止", 0, 0),
    CIRCULATE_CW("顺时针旋转", 0, 0),
    CIRCULATE_CCW("逆时针旋转", 0, 0);

    private final String displayName;
    private final double vectorX;
    private final double vectorY;

    WindDirection(String displayName, double vectorX, double vectorY) {
        this.displayName = displayName;
        this.vectorX = vectorX;
        this.vectorY = vectorY;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getVectorX() {
        return vectorX;
    }

    public double getVectorY() {
        return vectorY;
    }

    public boolean isCircular() {
        return this == CIRCULATE_CW || this == CIRCULATE_CCW;
    }
}
