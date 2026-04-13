package com.openrobotics.robot;

/** algorithm type enum; maps config strategy name strings to enum values */
public enum AlgorithmType {
    GREEDY, BUG, RTA_STAR, RANDOM, NONE;

    /** returns the AlgorithmType for {@code name}, case-insensitive; returns NONE if unrecognized */
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
