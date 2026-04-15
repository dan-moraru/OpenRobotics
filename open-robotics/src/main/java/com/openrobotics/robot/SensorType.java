package com.openrobotics.robot;

/** Sensor type enum; maps config sensor name strings to enum values. */
public enum SensorType {
    PROXIMITY, RANGE, NONE;

    /**
     * Returns the {@code SensorType} for {@code name}, case-insensitive.
     *
     * @param name the config string (e.g. {@code "PROXIMITY"}, {@code "RANGE"})
     * @return the matching type, or {@code NONE} if the name is {@code null} or unrecognized
     */
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
