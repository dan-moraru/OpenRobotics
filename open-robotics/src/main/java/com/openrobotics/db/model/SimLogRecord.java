package com.openrobotics.db.model;

import java.util.UUID;

public class SimLogRecord {

    private Long id;
    private UUID runId;
    private Integer tick;
    private Integer robotId;
    private String eventType;
    private Integer x;
    private Integer y;
    private String details;

    public SimLogRecord() {}

    /**
     * Gets the ID of the simulation log record.
     * @return ID of the simulation log record
     */
    public Long getId() {
        return id; 
    }

    /**
     * Sets the ID of the simulation log record.
     * @param id ID of the simulation log record
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Gets the ID of the run of the simulation log record.
     * @return ID of the run of the simulation log record
     */ 
    public UUID getRunId() {
        return runId; 
    }

    /**
     * Sets the ID of the run of the simulation log record.
     * @param runId ID of the run of the simulation log record
     */
    public void setRunId(UUID runId) {
        this.runId = runId; 
    }

    /**
     * Gets the tick of the simulation log record.
     * @return Tick of the simulation log record
     */
    public Integer getTick() {
        return tick;
    }

    /**
     * Sets the tick of the simulation log record.
     * @param tick Tick of the simulation log record
     */
    public void setTick(Integer tick) {
        this.tick = tick;
    }

    /**
     * Gets the ID of the robot of the simulation log record.
     * @return ID of the robot of the simulation log record
     */
    public Integer getRobotId() {
        return robotId;
    }

    /**
     * Sets the ID of the robot of the simulation log record.
     * @param robotId ID of the robot of the simulation log record
     */
    public void setRobotId(Integer robotId) {
        this.robotId = robotId; 
    }

    /**
     * Gets the event type of the simulation log record.
     * @return Event type of the simulation log record
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Sets the event type of the simulation log record.
     * @param eventType Event type of the simulation log record
     */
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    /**
     * Gets the X coordinate of the simulation log record.
     * @return X coordinate of the simulation log record
     */ 
    public Integer getX() {
        return x;
    }

    /**
     * Sets the X coordinate of the simulation log record.
     * @param x X coordinate of the simulation log record
     */
    public void setX(Integer x) {
        this.x = x;
    }

    /**
     * Gets the Y coordinate of the simulation log record.
     * @return Y coordinate of the simulation log record
     */
    public Integer getY() {
        return y;
    }

    /**
     * Sets the Y coordinate of the simulation log record.
     * @param y Y coordinate of the simulation log record
     */
    public void setY(Integer y) {
        this.y = y; 
    }

    /**
     * Gets the details of the simulation log record.
     * @return Details of the simulation log record
     */
    public String getDetails() { 
        return details; 
    }

    /**
     * Sets the details of the simulation log record.
     * @param details Details of the simulation log record
     */
    public void setDetails(String details) { 
        this.details = details;
    }
}
