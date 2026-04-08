package com.openrobotics.logging;

import com.openrobotics.db.dao.SimLogDao;
import com.openrobotics.db.dao.SimulationRunDao;
import com.openrobotics.db.dao.WorkloadTaskDao;
import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.db.recordbuilders.SimulationRunRecordBuilder;
import com.openrobotics.logging.eventtypes.RobotEvent;
import com.openrobotics.logging.eventtypes.SimulationRunEvent;
import com.openrobotics.logging.eventtypes.TaskEvent;

/**
 * Central logging access: allows logging from anywhere in the application.
 * There are 3 main event categories to create logs for: task events, simulation run events, and robot events.
 */
// TODO: Add unit tests for logging methods
public class Logger {

    private Logger() { }

    /**
     * Logs task events into the run_workload_task table in the database.
     * A task event can be for task creation, task assignment, and task completion.
     * @param eventType the task event type
     * @param record the workload task record containing the relevant information for the event being logged
     * @return the artifical ID of the logged task event (for task creation) or -1 for other event types
     */
    public static long logTaskEvent(TaskEvent eventType, WorkloadTaskRecord record) {
        try {
            switch (eventType) {
                case TASK_CREATED:
                    // Insert a new record for the created task
                    return WorkloadTaskDao.insert(record);
                case TASK_ASSIGNED:
                    // Update the existing record to set the assigned robot and tick
                    WorkloadTaskDao.assignToRobot(record.getId(), record.getAssignedRobotId(), record.getAssignedTick());
                    return -1;
                case TASK_COMPLETED:
                    // Update the existing record to set the completed tick and status
                    WorkloadTaskDao.markCompleted(record.getId(), record.getStatus(), record.getCompletedTick());
                   return -1;
                default:
                    throw new IllegalStateException("Unsupported event type: " + eventType);
            }
        } catch (Exception e) {
            System.err.println("Failed to log task event of type: " + eventType);
            System.err.println("Exception message: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Logs simulation run events into the simulation_runs table in the database.
     * A simulation run event can be for simulation run start, completion, and failure.
     * @param eventType the simulation run event type
     * @param record the simulation run record containing the relevant information for the event being logged
     */
    public static void logSimulationRunEvent(SimulationRunEvent eventType, SimulationRunRecord record) {
        try {
            switch (eventType) {
                case RUN_STARTED:
                    // Insert a new record for the simulation run start
                    SimulationRunDao.insert(record);
                    break;
                case RUN_COMPLETED:
                    // Update the existing record to set the finished at timestamp and status
                    SimulationRunDao.updateStatus(record.getId(), record.getStatus(), record.getFinishedAt());
                    break;
                case RUN_FAILED:
                    // TODO
                    break;
                default:
                    throw new IllegalStateException("Unsupported event type: " + eventType);
            }
        } catch (Exception e) {
            System.err.println("Failed to log simulation run event of type: " + eventType);
            System.err.println("Exception message: " + e.getMessage());
        }
    }

    /**
     * Logs robot events into the sim_log table in the database.
     * A robot event can be for robot movements, collisions, near misses, battery changes, and deadlock detections.
     * @param eventType the robot event type
     * @param record the simulation log record containing the relevant information for the robot event being logged
     */
    public static void logRobotEvent(RobotEvent eventType, SimLogRecord record) {
        try {
            // Insert a new record for the robot event
            SimLogDao.insert(record);
        } catch (Exception e) {
            System.err.println("Failed to log robot event of type: " + eventType);
            System.err.println("Exception message: " + e.getMessage());
        }
    }
}
