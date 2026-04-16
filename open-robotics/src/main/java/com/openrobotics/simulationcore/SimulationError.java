package com.openrobotics.simulationcore;

/** Terminal error states that halt the simulation. */
public enum SimulationError {
    NONE("No errors detected. Simulation is running smoothly."),
    ALL_ROBOTS_DEAD("All robots have depleted their batteries. Simulation cannot continue.\nConsider adding more charging stations to the map."),
    NO_ROBOTS_SPAWNED("No robots were spawned in the simulation. Simulation cannot run.\nPlease add robots to the simulation"),
    INVALID_MAP_CONFIGURATION("The map configuration is invalid. Simulation cannot run.\nEnsure the map has at least one rack and one delivery station."),
    NO_TASKS_ASSIGNED("No tasks were added to the simulation. Simulation cannot run.\nPlease manually add tasks to the simulation by configuring racks on the map."),
    NO_TASKS_GENERATED("No tasks were generated for the simulation. Simulation cannot continue.\nThere was likely an error with automatic task generation.");

    private final String message;

    SimulationError(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
