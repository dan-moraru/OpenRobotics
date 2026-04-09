package com.openrobotics.logging;

/**
 * Enum to specify the logging mode for the application.
 * DB mode logs events to the database, while NO_OP mode ignores all logging calls.
 * This allows for easy switching between full logging and a no-op logger for testing.
 */
public enum LoggerMode {
    DB,
    NO_OP
}
