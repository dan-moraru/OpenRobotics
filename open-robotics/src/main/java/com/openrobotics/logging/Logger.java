package com.openrobotics.logging;

import com.openrobotics.db.dao.SimLogDao;
import com.openrobotics.db.dao.SimulationRunDao;
import com.openrobotics.db.dao.WorkloadTaskDao;
import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.logging.eventtypes.RobotEvent;
import com.openrobotics.logging.eventtypes.SimulationRunEvent;
import com.openrobotics.logging.eventtypes.TaskEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Central simulation logger; robot events are buffered in a background queue, while task and
 * simulation events are written synchronously.
 */
public class Logger {
    private static LoggerMode mode = LoggerMode.DB;
    private static final BlockingQueue<SimLogRecord> robotEventQueue = new LinkedBlockingQueue<>();

    // background worker to flush robot events in batches for better performance during simulation ticks
    static {
        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000); // flush every 1 second

                    List<SimLogRecord> batch = new ArrayList<>();
                    robotEventQueue.drainTo(batch);

                    if (!batch.isEmpty()) {
                        long start = System.currentTimeMillis();
                        SimLogDao.insertBatch(batch);
                        System.out.println("[Logger] Flushed " + batch.size() + " robot events in " + (System.currentTimeMillis() - start) + " ms");
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
     * Sets the logging mode. {@link LoggerMode#NO_OP} suppresses all logging calls.
     *
     * @param newMode the new logging mode
     */
    public static void setMode(LoggerMode newMode) {
        mode = newMode;
    }

    /**
     * Logs a task lifecycle event to the {@code run_workload_tasks} table.
     *
     * @param eventType the task event type to persist
     * @param record the workload task record containing the event payload
     * @return the inserted record ID for {@link TaskEvent#TASK_CREATED}, or {@code -1} for other event types
     */
    public static long logTaskEvent(TaskEvent eventType, WorkloadTaskRecord record) {
        if (mode == LoggerMode.NO_OP) {
            return -1;
        }
        if (mode == null) {
            System.err.println("Failed to log task event of type: " + eventType);
            return -1;
        }

        try {
            switch (eventType) {
                case TASK_CREATED:
                    return WorkloadTaskDao.insert(record);
                case TASK_ASSIGNED:
                    WorkloadTaskDao.assignToRobot(record.getId(), record.getAssignedRobotId(), record.getAssignedTick());
                    return -1;
                case TASK_COMPLETED:
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
     * Logs a simulation run lifecycle event to the {@code simulation_runs} table.
     *
     * @param eventType the simulation run event type to persist
     * @param record the simulation run record containing the event payload
     */
    public static void logSimulationRunEvent(SimulationRunEvent eventType, SimulationRunRecord record) {
        if (mode == LoggerMode.NO_OP) {
            return;
        }

        try {
            switch (eventType) {
                case RUN_STARTED:
                    SimulationRunDao.insert(record);
                    break;
                case RUN_COMPLETED:
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
     * Enqueues a robot event for asynchronous batch write to the {@code sim_logs} table.
     * The background worker flushes the queue about once per second.
     *
     * @param eventType the robot event type associated with the log record
     * @param record the simulation log record to enqueue
     */
    public static void logRobotEvent(RobotEvent eventType, SimLogRecord record) {
        if (mode == LoggerMode.NO_OP) {
            return;
        }

        robotEventQueue.offer(record);
    }

    /** Flushes all pending robot events immediately. */
    public static void flushRobotEvents() {
        List<SimLogRecord> batch = new ArrayList<>();
        robotEventQueue.drainTo(batch);

        if (!batch.isEmpty()) {
            try {
                SimLogDao.insertBatch(batch);
            } catch (Exception e) {
                System.err.println("Failed to flush robot events: " + e.getMessage());
            }
        }
    }
}
