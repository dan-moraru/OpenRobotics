package com.openrobotics.logging.eventtypes;

/** Lifecycle events for a simulation run; used to categorize {@code simulation_runs} table updates. */
public enum SimulationRunEvent {
    RUN_STARTED,
    RUN_COMPLETED,
    RUN_FAILED
}
