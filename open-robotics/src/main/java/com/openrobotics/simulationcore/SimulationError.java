package com.openrobotics.simulationcore;

/** terminal error states that halt the simulation */
public enum SimulationError {
    NONE("No errors detected. Simulation is running smoothly."),
    ALL_ROBOTS_DEAD("All robots have depleted their batteries. Simulation cannot continue.\nConsider adding more charging stations to the map."),
    NO_ROBOTS_SPAWNED("No robots were spawned in the simulation. Simulation cannot run.\nPlease add robots to the simulation"),
    INVALID_MAP_CONFIGURATION("The map configuration is invalid. Simulation cannot run.\nEnsure the map has at least one rack and one delivery station.");

    private final String message;

    SimulationError(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
