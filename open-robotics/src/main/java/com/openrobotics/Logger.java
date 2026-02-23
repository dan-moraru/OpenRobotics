package com.openrobotics;

import java.util.ArrayList;
import java.util.List;

// per-tick event logging stub (real implementation deferred to logging/metrics task)
public class Logger {
    private final List<String> events;

    public Logger() {
        this.events = new ArrayList<>();
    }

    // log a simulation event
    public void logEvent(int tick, int robotId, String eventType, Vector2D position, String details) {
        events.add("tick=" + tick + " robot=" + robotId + " event=" + eventType
                + " pos=" + position + " details=" + details);
    }

    public List<String> getEvents() {
        return events;
    }
}
