package com.openrobotics.robot;

/** sensor type enum; maps config sensor name strings to enum values */
public enum SensorType {
    PROXIMITY, RANGE, NONE;

    /** returns the SensorType for {@code name}, case-insensitive; returns NONE if unrecognized */
    public static SensorType fromConfigString(String name) {
        if (name == null) return NONE;
        String upper = name.toUpperCase().trim();
        return switch (upper) {
            case "PROXIMITY", "PROXIMITYSENSOR" -> PROXIMITY;
            case "RANGE", "RANGESENSOR" -> RANGE;
            default -> NONE;
        };
    }
}
