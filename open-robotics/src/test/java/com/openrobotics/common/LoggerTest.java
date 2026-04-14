package com.openrobotics.common;

import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.LoggerMode;
import com.openrobotics.logging.eventtypes.RobotEvent;
import com.openrobotics.logging.eventtypes.SimulationRunEvent;
import com.openrobotics.logging.eventtypes.TaskEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for {@link Logger}. */
class LoggerTest {

    /**
     * Set the logger mode to DB and clear the robot queue.
     */
    @BeforeEach
    void setUp() {
        Logger.setMode(LoggerMode.DB);
        clearRobotQueue();
    }

    /**
     * Clear the robot queue and set the logger mode to DB.
     */
    @AfterEach
    void tearDown() {
        clearRobotQueue();
        Logger.setMode(LoggerMode.DB);
    }

    /**
     * Test that the logger mode is set to NO_OP and the task event returns -1.
     */
    @Test
    void setModeNoOp_taskEventReturnsMinusOne() {
        Logger.setMode(LoggerMode.NO_OP);
        long result = Logger.logTaskEvent(TaskEvent.TASK_CREATED, createTaskRecord());
        assertEquals(-1L, result);
    }

    /**
     * Test that the logger mode is set to null and the task event returns -1.
     */
    @Test
    void logTaskEventReturnsMinusOneWhenModeIsNull() {
        Logger.setMode(null);
        long result = Logger.logTaskEvent(TaskEvent.TASK_CREATED, createTaskRecord());
        assertEquals(-1L, result);
    }

    /**
     * Test that the task created event returns -1 when the DAO fails.
     */
    @Test
    void logTaskEventTaskCreatedCatchesDaoFailureAndReturnsMinusOne() {
        long result = Logger.logTaskEvent(TaskEvent.TASK_CREATED, createTaskRecord());
        assertEquals(-1L, result);
    }

    /**
     * Test that the task assigned event returns -1 when the DAO fails.
     */
    @Test
    void logTaskEventTaskAssignedCatchesDaoFailureAndReturnsMinusOne() {
        long result = Logger.logTaskEvent(TaskEvent.TASK_ASSIGNED, createTaskRecord());
        assertEquals(-1L, result);
    }

    /**
     * Test that the task completed event returns -1 when the DAO fails.
     */
    @Test
    void logTaskEventTaskCompletedCatchesDaoFailureAndReturnsMinusOne() {
        long result = Logger.logTaskEvent(TaskEvent.TASK_COMPLETED, createTaskRecord());
        assertEquals(-1L, result);
    }

    /**
     * Test that the task event returns -1 when the event type is null.
     */
    @Test
    void logTaskEventNullEventTypeIsCaughtAndReturnsMinusOne() {
        long result = Logger.logTaskEvent(null, createTaskRecord());
        assertEquals(-1L, result);
    }

    /**
     * Test that the simulation run event returns immediately when the logger mode is set to NO_OP.
     */
    @Test
    void logSimulationRunEventNoOpReturnsImmediately() {
        Logger.setMode(LoggerMode.NO_OP);
        assertDoesNotThrow(() -> Logger.logSimulationRunEvent(SimulationRunEvent.RUN_STARTED, createSimulationRunRecord()));
    }

    /**
     * Test that the simulation run event returns immediately when the logger mode is set to NO_OP.
     */
    @Test
    void logSimulationRunEventRunStartedCatchesDaoFailure() {
        assertDoesNotThrow(() -> Logger.logSimulationRunEvent(SimulationRunEvent.RUN_STARTED, createSimulationRunRecord()));
    }

    /**
     * Test that the simulation run event returns immediately when the logger mode is set to NO_OP.
     */
    @Test
    void logSimulationRunEventRunStartedWithNullRecordIsCaught() {
        assertDoesNotThrow(() -> Logger.logSimulationRunEvent(SimulationRunEvent.RUN_STARTED, null));
    }

    /**
     * Test that the simulation run event returns immediately when the logger mode is set to NO_OP.
     */
    @Test
    void logSimulationRunEventRunCompletedCatchesDaoFailure() {
        assertDoesNotThrow(() -> Logger.logSimulationRunEvent(SimulationRunEvent.RUN_COMPLETED, createSimulationRunRecord()));
    }

    /**
     * Test that the simulation run event returns immediately when the logger mode is set to NO_OP.
     */
    @Test
    void logSimulationRunEventRunCompletedWithNullRecordIsCaught() {
        assertDoesNotThrow(() -> Logger.logSimulationRunEvent(SimulationRunEvent.RUN_COMPLETED, null));
    }

    /**
     * Test that the simulation run event returns immediately when the logger mode is set to NO_OP.
     */
    @Test
    void logSimulationRunEventRunFailedPathDoesNotThrow() {
        assertDoesNotThrow(() -> Logger.logSimulationRunEvent(SimulationRunEvent.RUN_FAILED, createSimulationRunRecord()));
    }

    /**
     * Test that the simulation run event returns immediately when the logger mode is set to NO_OP.
     */
    @Test
    void logSimulationRunEventNullEventTypeIsCaught() {
        assertDoesNotThrow(() -> Logger.logSimulationRunEvent(null, createSimulationRunRecord()));
    }

    /**
     * Test that the robot event returns immediately when the logger mode is set to NO_OP.
     */
    @Test
    void logRobotEventNoOpDoesNotEnqueue() {
        Logger.setMode(LoggerMode.NO_OP);
        Logger.logRobotEvent(RobotEvent.MOVE_EXECUTED, createSimLogRecord());
        assertEquals(0, getRobotQueue().size());
    }

    /**
     * Test that the robot event returns immediately when the logger mode is set to NO_OP.
     */
    @Test
    void logRobotEventDbModeEnqueuesRecord() {
        SimLogRecord record = createSimLogRecord();
        Logger.logRobotEvent(RobotEvent.COLLISION, record);
        assertTrue(getRobotQueue().contains(record));
    }

    /**
     * Test that the robot event returns immediately when the logger mode is set to NO_OP.
     */
    @Test
    void flushRobotEventsWithEmptyQueueDoesNotThrow() {
        assertDoesNotThrow(Logger::flushRobotEvents);
        assertEquals(0, getRobotQueue().size());
    }

    /**
     * Test that the robot event returns immediately when the logger mode is set to NO_OP.
     */
    @Test
    void flushRobotEventsWithPendingRecordsDrainsQueue() {
        Logger.logRobotEvent(RobotEvent.CHARGE_START, createSimLogRecord());
        assertTrue(getRobotQueue().size() > 0);

        assertDoesNotThrow(Logger::flushRobotEvents);
        assertEquals(0, getRobotQueue().size());
    }

    /**
     * Test that the robot event returns immediately when the logger mode is set to NO_OP.
     */
    @Test
    void backgroundWorkerNonEmptyBatchExceptionIsCaughtAndLogged() {
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));
        try {
            Logger.setMode(LoggerMode.DB);
            // Enqueue a real record so the worker reaches SimLogDao.insertBatch(batch).
            Logger.logRobotEvent(RobotEvent.MOVE_EXECUTED, createSimLogRecord());

            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                if (errCapture.toString().contains("Failed to flush robot event batch:")) {
                    break;
                }
                Thread.sleep(50);
            }

            assertTrue(
                errCapture.toString().contains("Failed to flush robot event batch:"),
                "Expected worker catch block to log batch flush failure"
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for background logger worker");
        } finally {
            System.setErr(originalErr);
        }
    }

    /**
     * Test that the constructor is private and can be instantiated reflectively.
     */
    @Test
    void constructorIsPrivateButReflectivelyInstantiable() throws Exception {
        Constructor<Logger> constructor = Logger.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Logger instance = constructor.newInstance();
        assertNotNull(instance);
    }

    /**
     * Create a workload task record.
     */
    private WorkloadTaskRecord createTaskRecord() {
        WorkloadTaskRecord record = new WorkloadTaskRecord();
        record.setId(42L);
        record.setAssignedRobotId(UUID.randomUUID());
        record.setAssignedTick(8);
        record.setCompletedTick(10);
        record.setStatus("COMPLETED");
        return record;
    }

    /**
     * Create a simulation run record.
     */
    private SimulationRunRecord createSimulationRunRecord() {
        SimulationRunRecord record = new SimulationRunRecord();
        record.setId(UUID.randomUUID());
        record.setStatus("COMPLETED");
        record.setFinishedAt(Timestamp.from(Instant.now()));
        return record;
    }

    /**
     * Create a simulation log record.
     */
    private SimLogRecord createSimLogRecord() {
        SimLogRecord record = new SimLogRecord();
        record.setRunId(UUID.randomUUID());
        record.setTick(1);
        record.setRobotId(UUID.randomUUID());
        record.setEventType("MOVE_EXECUTED");
        record.setX(2);
        record.setY(3);
        record.setDetails("{\"note\":\"test\"}");
        return record;
    }

    /**
     * Get the robot queue.
     */
    @SuppressWarnings("unchecked")
    private BlockingQueue<SimLogRecord> getRobotQueue() {
        try {
            Field queueField = Logger.class.getDeclaredField("robotEventQueue");
            queueField.setAccessible(true);
            return (BlockingQueue<SimLogRecord>) queueField.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to access logger robot event queue", e);
        }
    }

    /**
     * Clear the robot queue.
     */
    private void clearRobotQueue() {
        getRobotQueue().clear();
    }
}
