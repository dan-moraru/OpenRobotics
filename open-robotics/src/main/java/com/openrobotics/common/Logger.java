package com.openrobotics.common;

import com.openrobotics.map.Vector2D;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Central logging access: allows logging from anywhere in the application.
 * Call {@link #init()} once before using the logger (from MainApp.java).
 */
public class Logger {
    private static Logger instance;
    private final List<String> events;

    private Logger() {
        this.events = new ArrayList<>();
    }

    /**
     * Initializes the logger.
     * Safe to call multiple times; subsequent calls are no-ops after the first successful init.
     */
    public static synchronized void init() {
        if (instance == null) {
            instance = new Logger();
        }
    }

    /**
     * Returns the shared logger instance. {@link #init()} must have been called first.
     * @throws IllegalStateException if the logger has not been initialized yet.
     */
    public static Logger getInstance() throws IllegalStateException{
        if (instance == null) {
            throw new IllegalStateException("Logger not initialized. Call Logger.init() at startup.");
        }
        return instance;
    }

    // log a simulation event
    public void logEvent(int tick, int robotId, Event eventType, Vector2D position, String details) {
        events.add("tick=" + tick + " robot=" + robotId + " event=" + eventType
                + " pos=" + position + " details=" + details);
    }

    public List<String> getEvents() {
        return events;
    }
}
