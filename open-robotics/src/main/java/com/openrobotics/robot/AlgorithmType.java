package com.openrobotics.robot;

// maps config strategy names ("GREEDY" or "GREEDYNAVIGATIONSTRATEGY") to enums
public enum AlgorithmType {
    GREEDY, BUG, RTA_STAR, RANDOM, NONE;

    // maps known config aliases to enum values, case-insensitive
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
