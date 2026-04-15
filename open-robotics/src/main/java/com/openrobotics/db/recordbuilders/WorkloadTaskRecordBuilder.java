package com.openrobotics.db.recordbuilders;

import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.task.Task;

import java.util.UUID;

/** Builds {@link WorkloadTaskRecord} instances for task lifecycle events. */
public class WorkloadTaskRecordBuilder {
    private WorkloadTaskRecord record;

    /**
     * Creates a builder pre-filled from the given task, populating type, priority, locations, and status.
     *
     * @param simulationRunId the UUID of the simulation run this task belongs to
     * @param task the task to build a record from
     */
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

    /**
     * Builds the task creation record; sets {@code createdTick}.
     *
     * @param creationTick the simulation tick at which the task was created
     * @return the populated workload task record
     */
    public WorkloadTaskRecord buildTaskCreationRecord(int creationTick) {
        record.setCreatedTick(creationTick);
        return record;
    }

    /**
     * Builds the task assignment record; sets {@code assignedTick} and {@code assignedRobotId}.
     *
     * @param assignedTick the simulation tick at which the task was assigned
     * @param assignedRobotId the UUID of the robot assigned to this task
     * @return the populated workload task record
     */
    public WorkloadTaskRecord buildTaskAssignmentRecord(int assignedTick, UUID assignedRobotId) {
        record.setAssignedTick(assignedTick);
        record.setAssignedRobotId(assignedRobotId);
        return record;
    }

    /**
     * Builds the task completion record; sets {@code completedTick} and status to {@code COMPLETED}.
     *
     * @param completedTick the simulation tick at which the task was completed
     * @return the populated workload task record
     */
    public WorkloadTaskRecord buildTaskCompletionRecord(int completedTick) {
        record.setCompletedTick(completedTick);
        record.setStatus("COMPLETED");
        return record;
    }
}
