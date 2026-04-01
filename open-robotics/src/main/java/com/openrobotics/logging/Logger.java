package com.openrobotics.logging;

import com.openrobotics.db.dao.WorkloadTaskDao;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.logging.eventtypes.TaskEvent;

/**
 * Central logging access: allows logging from anywhere in the application.
 */
// TODO: Add unit tests for logging methods
public class Logger {

    private Logger() { }

    /**
     * Logs task events into the run_workload_task table in the database.
     * A task event can be for task creation, task assignment, and task completion.
     * @param eventType the task event type
     * @param record the workload task record containing the relevant information for the event being logged
     */
    public static void logTaskEvent(TaskEvent eventType, WorkloadTaskRecord record) {
        try {
            switch (eventType) {
                case TASK_CREATED:
                    WorkloadTaskDao.insert(record);
                    break;
                case TASK_ASSIGNED:
                    WorkloadTaskDao.assignToRobot(record.getId(), record.getAssignedRobotId(), record.getAssignedTick());
                    break;
                case TASK_COMPLETED:
                    WorkloadTaskDao.markCompleted(record.getId(), record.getStatus(), record.getCompletedTick());
                    break;
                default:
                    throw new IllegalStateException("Unsupported event type: " + eventType);
            }
        } catch (Exception e) {
            // Log the exception and rethrow it to be handled by the caller
            System.err.println("Failed to log task event of type: " + eventType);
            System.err.println("Exception message: " + e.getMessage());
        }
    }


}
