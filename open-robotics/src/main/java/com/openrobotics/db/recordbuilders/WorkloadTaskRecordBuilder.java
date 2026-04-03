package com.openrobotics.db.recordbuilders;

import com.openrobotics.AppState;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.task.Task;

import java.util.UUID;

public class WorkloadTaskRecordBuilder {
    private WorkloadTaskRecord record;

    /**
     * Creates a new WorkloadTaskRecordBuilder and initializes the record with the given task
     * for general task information.
     * @param task Task to initialize the record with.
     */
    public WorkloadTaskRecordBuilder(Task task) {
        this.record = new WorkloadTaskRecord();
        record.setId(task.getId());
        record.setRunId(AppState.getEngine().getRunId());
        record.setTaskType(task.getClass().getSimpleName());
        record.setPriority(task.getPriority());
        record.setPickupX(task.getPickupLocation().getX());
        record.setPickupY(task.getPickupLocation().getY());
        record.setDropoffX(task.getDropoffLocation().getX());
        record.setDropoffY(task.getDropoffLocation().getY());
        record.setStatus(task.getStatus().name());
    }

    /**
     * Builds a WorkloadTaskRecord for a task creation event by setting the created tick field.
     * @param creationTick the tick number that the task was created on
     * @return a WorkloadTaskRecord for a task creation event
     */
    public WorkloadTaskRecord buildTaskCreationRecord(int creationTick) {
        record.setCreatedTick(creationTick);
        return record;
    }

    /**
     * Builds a WorkloadTaskRecord for a task assignment event by setting the assigned tick and
     * assigned robot ID fields.
     * @param assignedTick the tick number that the task was assigned on
     * @param assignedRobotId the ID of the robot that the task was assigned to
     * @return a WorkloadTaskRecord for a task assignment event
     */
    public WorkloadTaskRecord buildTaskAssignmentRecord(int assignedTick, UUID assignedRobotId) {
        record.setAssignedTick(assignedTick);
        record.setAssignedRobotId(assignedRobotId);
        return record;
    }

    /**
     * Builds a WorkloadTaskRecord for a task completion event by setting the completed tick field.
     * @param completedTick the tick number that the task was completed on
     * @return a WorkloadTaskRecord for a task completion event
     */
    public WorkloadTaskRecord buildTaskCompletionRecord(int completedTick) {
        record.setCompletedTick(completedTick);
        return record;
    }
}
