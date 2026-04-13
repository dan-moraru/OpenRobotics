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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Central logging access: allows logging from anywhere in the application.
 * There are 3 main event categories to create logs for: task events, simulation run events, and robot events.
 */
// TODO: Add unit tests for logging methods
public class Logger {
    private static LoggerMode mode = LoggerMode.DB;
    private static final BlockingQueue<SimLogRecord> robotEventQueue = new LinkedBlockingQueue<>();

    // Background worker to flush robot events in batches for better performance during simulation ticks
    static {
        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); // flush every 5 seconds

                    List<SimLogRecord> batch = new ArrayList<>();
                    robotEventQueue.drainTo(batch, 100); // flush up to 100 events at a time

                    if (!batch.isEmpty()) {
                        SimLogDao.insertBatch(batch);
                    }

                } catch (Exception e) {
                    System.err.println("Failed to flush robot event batch: " + e.getMessage());
                }
            }
        });

        worker.setDaemon(true); // doesn't block app shutdown
        worker.start();
    }

    private Logger() { }

    /**
     * Sets the logger mode. In DB mode, logs are written to the database. In NO_OP mode,
     * logging calls are ignored.
     * @param newMode the new logger mode to set
     */
    public static void setMode(LoggerMode newMode) {
        mode = newMode;
    }

    /**
     * Logs task events into the run_workload_task table in the database.
     * A task event can be for task creation, task assignment, and task completion.
     * @param eventType the task event type
     * @param record the workload task record containing the relevant information for the event being logged
     * @return the artifical ID of the logged task event (for task creation) or -1 for other event types
     */
    public static long logTaskEvent(TaskEvent eventType, WorkloadTaskRecord record) {
        if (mode == LoggerMode.NO_OP) {
            return -1; // no-op mode, do not log anything
        }
        if (mode == null) {
            System.err.println("Failed to log task event of type: " + eventType);
            return -1;
        }

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
        if (mode == LoggerMode.NO_OP) {
            return; // no-op mode, do not log anything
        }

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
     * Logs robot events into the sim_logs table in the database.
     * A robot event can be for robot movement, picking up an item, dropping off an item, etc.
     * Robot events are logged asynchronously using a background worker thread that flushes events in
     * batches for better performance during simulation ticks.
     * @param eventType the robot event type
     * @param record the simulation log record containing the relevant information for the robot event being logged
     */
    public static void logRobotEvent(RobotEvent eventType, SimLogRecord record) {
        if (mode == LoggerMode.NO_OP) {
            return; // no-op mode, do not log anything
        }

        robotEventQueue.offer(record);
    }
}
