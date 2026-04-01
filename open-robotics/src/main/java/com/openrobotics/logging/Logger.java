package com.openrobotics.logging;

import com.openrobotics.db.dao.WorkloadTaskDao;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.logging.eventtypes.TaskEvent;

/**
 * Central logging access: allows logging from anywhere in the application.
 * Call {@link #init()} once before using the logger (from MainApp.java).
 */
// TODO: Add unit tests for logging methods
public class Logger {
    private static Logger instance;

    private Logger() { }

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
    public static Logger getLogger() throws IllegalStateException{
        if (instance == null) {
            throw new IllegalStateException("Logger not initialized. Call Logger.init() to initialize the Logger.");
        }
        return instance;
    }

    /**
     * Logs task events into the run_workload_task table in the database.
     * A task event can be for task creation, task assignment, and task completion.
     * @param eventType the task event type
     * @param record the workload task record containing the relevant information for the event being logged
     * @throws IllegalStateException if attempting to log an event for an unsupported TaskEvent event type
     */
    public void logTaskEvent(TaskEvent eventType, WorkloadTaskRecord record) {
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
