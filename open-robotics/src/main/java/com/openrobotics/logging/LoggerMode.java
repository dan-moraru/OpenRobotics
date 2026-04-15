package com.openrobotics.logging;

/** Logging mode; {@code DB} writes events to the database, and {@code NO_OP} suppresses all logging calls. */
public enum LoggerMode {
    DB,
    NO_OP
}
