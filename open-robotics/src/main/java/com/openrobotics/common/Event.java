package com.openrobotics.common;

public enum Event {
    // Task events
    TASK_CREATED,
    TASK_ASSIGNED,
    TASK_COMPLETED,

    // Robot events
    MOVE_INTENT,
    MOVE_EXECUTED,
    COLLISION,
    NEAR_MISS,
    BATTERY_DEATH,
    CHARGE_START,
    CHARGE_END,
    DEADLOCK_DETECTED,
    DEADLOCK_RESOLVED,

    // Simulation run events
    RUN_STARTED,
    RUN_COMPLETED,
    RUN_FAILED
}
