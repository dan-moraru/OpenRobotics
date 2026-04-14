package com.openrobotics.db.model;

import java.util.UUID;

/** database record for the sim_logs table */
public class SimLogRecord {

    private Long id;
    private UUID runId;
    private Integer tick;
    private UUID robotId;
    private String eventType;
    private Integer x;
    private Integer y;
    private String details;

    public SimLogRecord() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public Integer getTick() {
        return tick;
    }

    public void setTick(Integer tick) {
        this.tick = tick;
    }

    public UUID getRobotId() {
        return robotId;
    }

    public void setRobotId(UUID robotId) {
        this.robotId = robotId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getX() {
        return x;
    }

    public void setX(Integer x) {
        this.x = x;
    }

    public Integer getY() {
        return y;
    }

    public void setY(Integer y) {
        this.y = y;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    @Override
    public String toString() {
        String out = "[Simulation Log] " + eventType + " at tick " + tick + " for robot " + robotId + " at position (" + x + ", " + y + ")";

        if (details != null) {
            out += "\nDetails: " + details;
        }

        return out;
    }
}

