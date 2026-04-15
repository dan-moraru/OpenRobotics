package com.openrobotics.robot;

/** Algorithm type enum; maps config strategy name strings to enum values. */
public enum AlgorithmType {
    GREEDY, BUG, RTA_STAR, RANDOM, NONE;

    /**
     * Returns the {@code AlgorithmType} for {@code name}, case-insensitive.
     *
     * @param name the config string (e.g. {@code "GREEDY"}, {@code "BUG"}, {@code "RTA_STAR"}, {@code "RANDOM"})
     * @return the matching type, or {@code NONE} if the name is {@code null} or unrecognized
     */
    public static AlgorithmType fromConfigString(String name) {
        if (name == null) return NONE;
        String upper = name.toUpperCase().trim();
        return switch (upper) {
            case "GREEDY", "GREEDYNAVIGATIONSTRATEGY" -> GREEDY;
            case "BUG", "BUGNAVIGATIONSTRATEGY" -> BUG;
            case "RTA_STAR", "RTASTARNAVIGATIONSTRATEGY" -> RTA_STAR;
            case "RANDOM", "RANDOMNAVIGATIONSTRATEGY" -> RANDOM;
            default -> NONE;
        };
    }
}
