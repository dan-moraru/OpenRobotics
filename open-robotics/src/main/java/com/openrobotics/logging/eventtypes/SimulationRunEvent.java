package com.openrobotics.logging.eventtypes;

/** lifecycle events for a simulation run; used to categorize simulation_runs table updates */
public enum SimulationRunEvent {
    RUN_STARTED,
    RUN_COMPLETED,
    RUN_FAILED
}
