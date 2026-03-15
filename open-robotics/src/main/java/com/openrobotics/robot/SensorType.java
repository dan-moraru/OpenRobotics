package com.openrobotics.robot;

/**
 * Maps sensory strategy type to a string
 */
public enum SensorType {
    PROXIMITY, RANGE, NONE;

    // maps known config aliases to enum values, case-insensitive
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
