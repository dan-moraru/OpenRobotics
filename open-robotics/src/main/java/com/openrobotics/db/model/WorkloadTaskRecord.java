package com.openrobotics.db.model;

import java.util.UUID;

/** database record for the run_workload_tasks table */
public class WorkloadTaskRecord {

    private Long id;
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
    private UUID assignedRobotId;
    private String details;

    public WorkloadTaskRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Integer getCreatedTick() { return createdTick; }
    public void setCreatedTick(Integer createdTick) { this.createdTick = createdTick; }

    public Integer getAssignedTick() { return assignedTick; }
    public void setAssignedTick(Integer assignedTick) { this.assignedTick = assignedTick; }

    public Integer getCompletedTick() { return completedTick; }
    public void setCompletedTick(Integer completedTick) { this.completedTick = completedTick; }

    public Integer getPickupX() { return pickupX; }
    public void setPickupX(Integer pickupX) { this.pickupX = pickupX; }

    public Integer getPickupY() { return pickupY; }
    public void setPickupY(Integer pickupY) { this.pickupY = pickupY; }

    public Integer getDropoffX() { return dropoffX; }
    public void setDropoffX(Integer dropoffX) { this.dropoffX = dropoffX; }

    public Integer getDropoffY() { return dropoffY; }
    public void setDropoffY(Integer dropoffY) { this.dropoffY = dropoffY; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getAssignedRobotId() { return assignedRobotId; }
    public void setAssignedRobotId(UUID assignedRobotId) { this.assignedRobotId = assignedRobotId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
