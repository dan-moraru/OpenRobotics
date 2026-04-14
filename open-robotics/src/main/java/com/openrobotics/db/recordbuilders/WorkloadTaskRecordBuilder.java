package com.openrobotics.db.recordbuilders;

import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.task.Task;

import java.util.UUID;

/** builds WorkloadTaskRecord instances for task lifecycle events */
public class WorkloadTaskRecordBuilder {
    private WorkloadTaskRecord record;

    /** initializes a WorkloadTaskRecord from the given task, pre-filling type, priority, locations, and status */
    public WorkloadTaskRecordBuilder(UUID simulationRunId, Task task) {
        this.record = new WorkloadTaskRecord();
        record.setId(task.getArtificialId());
        record.setRunId(simulationRunId);
        record.setTaskType(task.getClass().getSimpleName());
        record.setPriority(task.getPriority());
        record.setPickupX(task.getPickupLocation().getX());
        record.setPickupY(task.getPickupLocation().getY());
        record.setDropoffX(task.getDropoffLocation().getX());
        record.setDropoffY(task.getDropoffLocation().getY());
        record.setStatus(task.getStatus().name());
    }

    /** builds the task creation record; sets createdTick */
    public WorkloadTaskRecord buildTaskCreationRecord(int creationTick) {
        record.setCreatedTick(creationTick);
        return record;
    }

    /** builds the task assignment record; sets assignedTick and assignedRobotId */
    public WorkloadTaskRecord buildTaskAssignmentRecord(int assignedTick, UUID assignedRobotId) {
        record.setAssignedTick(assignedTick);
        record.setAssignedRobotId(assignedRobotId);
        return record;
    }

    /** builds the task completion record; sets completedTick and status to COMPLETED */
    public WorkloadTaskRecord buildTaskCompletionRecord(int completedTick) {
        record.setCompletedTick(completedTick);
        record.setStatus("COMPLETED");
        return record;
    }
}
