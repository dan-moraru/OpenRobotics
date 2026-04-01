package com.openrobotics.db.model;

import com.openrobotics.task.Task;

import java.util.UUID;

public class WorkloadTaskRecord {

    private Integer id;
    private UUID runId;
    private String taskType;
    private Integer priority;
    private Integer createdTick;
    private Integer assignedTick;
    private Integer completedTick;
    private Integer pickupX;
    private Integer pickupY;
    private Integer dropoffX;
    private Integer dropoffY;
    private String status;
    private Integer assignedRobotId;
    private String details;

    public WorkloadTaskRecord() {}

    /**
     * Gets the ID of the workload task record.
     * @return ID of the workload task record
     */
    public Integer getId() {
        return id; 
    }

    /**
     * Sets the ID of the workload task record.
     * @param id ID of the workload task record
     */
    public void setId(Integer id) {
        this.id = id; 
    }

    /**
     * Gets the ID of the run of the workload task record.
     * @return ID of the run of the workload task record
     */
    public UUID getRunId() {
        return runId; 
    }

    /**
     * Sets the ID of the run of the workload task record.
     * @param runId ID of the run of the workload task record
     */
    public void setRunId(UUID runId) {
        this.runId = runId; 
    }

    /**
     * Gets the task type of the workload task record.
     * @return Task type of the workload task record
     */
    public String getTaskType() {
        return taskType; 
    }

    /**
     * Sets the task type of the workload task record.
     * @param taskType Task type of the workload task record
     */
    public void setTaskType(String taskType) {
        this.taskType = taskType; 
    }

    /**
     * Gets the priority of the workload task record.
     * @return Priority of the workload task record
     */
    public Integer getPriority() {
        return priority; 
    }

    /**
     * Sets the priority of the workload task record.
     * @param priority Priority of the workload task record
     */
    public void setPriority(Integer priority) {
        this.priority = priority; 
    }

    /**
     * Gets the created tick of the workload task record.
     * @return Created tick of the workload task record
     */ 
    public Integer getCreatedTick() {
        return createdTick; 
    }

    /**
     * Sets the created tick of the workload task record.
     * @param createdTick Created tick of the workload task record
     */
    public void setCreatedTick(Integer createdTick) {
        this.createdTick = createdTick; 
    }

    /**
     * Gets the assigned tick of the workload task record.
     * @return Assigned tick of the workload task record
     */
    public Integer getAssignedTick() {
        return assignedTick; 
    }

    /**
     * Sets the assigned tick of the workload task record.
     * @param assignedTick Assigned tick of the workload task record
     */
    public void setAssignedTick(Integer assignedTick) {
        this.assignedTick = assignedTick; 
    }

    /**
     * Gets the completed tick of the workload task record.
     * @return Completed tick of the workload task record
     */
    public Integer getCompletedTick() { 
        return completedTick; 
    }

    /**
     * Sets the completed tick of the workload task record.
     * @param completedTick Completed tick of the workload task record
     */
    public void setCompletedTick(Integer completedTick) {
        this.completedTick = completedTick; 
    }

    /**
     * Gets the pickup X coordinate of the workload task record.
     * @return Pickup X coordinate of the workload task record
     */
    public Integer getPickupX() {
        return pickupX; 
    }

    /**
     * Sets the pickup X coordinate of the workload task record.
     * @param pickupX Pickup X coordinate of the workload task record
     */
    public void setPickupX(Integer pickupX) { 
        this.pickupX = pickupX; 
    }

    /**
     * Gets the pickup Y coordinate of the workload task record.
     * @return Pickup Y coordinate of the workload task record
     */
    public Integer getPickupY() {
        return pickupY; 
    }

    /**
     * Sets the pickup Y coordinate of the workload task record.
     * @param pickupY Pickup Y coordinate of the workload task record
     */
    public void setPickupY(Integer pickupY) {
        this.pickupY = pickupY; 
    }

    /**
     * Gets the dropoff X coordinate of the workload task record.
     * @return Dropoff X coordinate of the workload task record
     */ 
    public Integer getDropoffX() {
        return dropoffX; 
    }

    /**
     * Sets the dropoff X coordinate of the workload task record.
     * @param dropoffX Dropoff X coordinate of the workload task record
     */
    public void setDropoffX(Integer dropoffX) {
        this.dropoffX = dropoffX; 
    }

    /**
     * Gets the dropoff Y coordinate of the workload task record.
     * @return Dropoff Y coordinate of the workload task record
     */
    public Integer getDropoffY() {
        return dropoffY; 
    }

    /**
     * Sets the dropoff Y coordinate of the workload task record.
     * @param dropoffY Dropoff Y coordinate of the workload task record
     */
    public void setDropoffY(Integer dropoffY) {
        this.dropoffY = dropoffY; 
    }

    /**
     * Gets the status of the workload task record.
     * @return Status of the workload task record
     */
    public String getStatus() {
        return status; 
    }

    /**
     * Sets the status of the workload task record.
     * @param status Status of the workload task record
     */
    public void setStatus(String status) {
        this.status = status; 
    }

    /**
     * Gets the ID of the assigned robot of the workload task record.
     * @return ID of the assigned robot of the workload task record
     */
    public Integer getAssignedRobotId() {
        return assignedRobotId; 
    }

    /**
     * Sets the ID of the assigned robot of the workload task record.
     * @param assignedRobotId ID of the assigned robot of the workload task record
     */
    public void setAssignedRobotId(Integer assignedRobotId) {
        this.assignedRobotId = assignedRobotId; 
    }

    /**
     * Gets the details of the workload task record.
     * @return Details of the workload task record
     */
    public String getDetails() {
        return details; 
    }

    /**
     * Sets the details of the workload task record.
     * @param details Details of the workload task record
     */
    public void setDetails(String details) { 
        this.details = details; 
    }
}
