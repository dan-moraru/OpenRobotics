package com.openrobotics.logging.eventtypes;

public enum RobotEvent {
    MOVE_INTENT,
    MOVE_EXECUTED,
    COLLISION,
    NEAR_MISS,
    BATTERY_DEATH,
    CHARGE_START,
    CHARGE_END,
    DEADLOCK_DETECTED,
    DEADLOCK_RESOLVED,
}
