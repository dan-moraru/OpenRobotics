package com.openrobotics.logging.eventtypes;

/** Event types emitted by robots during simulation; used to categorize {@code sim_logs} entries. */
public enum RobotEvent {
    MOVE_INTENT, // NOTE: Is this needed? Can't think of a use case for knowing these events
    MOVE_EXECUTED,
    COLLISION,
    NEAR_MISS,
    BATTERY_DEATH,
    CHARGE_START,
    CHARGE_END,
    DEADLOCK_DETECTED,
    DEADLOCK_RESOLVED,
}
