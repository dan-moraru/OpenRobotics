package com.openrobotics.logging;

import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.logging.eventtypes.RobotEvent;
import com.openrobotics.logging.eventtypes.SimulationRunEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for {@link Logger}.
 *
 * <p>This consolidated suite validates logger mode-gating semantics, task/simulation event
 * exception handling behavior, robot-event queueing and flushing paths, and private/static internal
 * state interactions used by the logger's asynchronous batch strategy.</p>
 */
class LoggerTest {

    /**
     * Resets logger mode and queue to a deterministic baseline before each test.
     */
    @BeforeEach
    void setUp() throws Exception {
        Logger.setMode(LoggerMode.NO_OP);
        robotQueue().clear();
    }

    /**
     * Restores logger mode and queue after each test to prevent cross-test interference.
     */
    @AfterEach
    void tearDown() throws Exception {
        Logger.setMode(LoggerMode.NO_OP);
        robotQueue().clear();
    }

    /**
     * Verifies the private constructor can be accessed reflectively for branch coverage.
     */
    @Test
    void constructor_is_private_but_reflection_can_instantiate_for_coverage() throws Exception {
        Constructor<Logger> constructor = Logger.class.getDeclaredConstructor();

        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);

        assertNotNull(constructor.newInstance());
    }

    /**
     * Verifies {@link Logger#setMode(LoggerMode)} updates the static mode field.
     */
    @Test
    void setMode_updates_static_logger_mode() throws Exception {
        Logger.setMode(LoggerMode.DB);
        assertSame(LoggerMode.DB, currentMode());

        Logger.setMode(LoggerMode.NO_OP);
        assertSame(LoggerMode.NO_OP, currentMode());
    }

    /**
     * Verifies NO_OP mode suppresses simulation-run logging across normal and null inputs.
     */
    @Test
    void noOpMode_ignores_simulation_run_events_including_nulls() {
        assertAll(
                () -> assertDoesNotThrow(() -> Logger.logSimulationRunEvent(SimulationRunEvent.RUN_STARTED, simulationRecord())),
                () -> assertDoesNotThrow(() -> Logger.logSimulationRunEvent(null, null))
        );
    }

    /**
     * Verifies NO_OP mode suppresses robot-event queueing entirely.
     */
    @Test
    void noOpMode_ignores_robot_events_and_does_not_enqueue_records() throws Exception {
        SimLogRecord record = simLogRecord();

        Logger.logRobotEvent(RobotEvent.MOVE_EXECUTED, record);
        Logger.logRobotEvent(null, null);

        assertTrue(robotQueue().isEmpty());
    }

    /**
     * Verifies DB mode simulation-run logging catches null event type failures.
     */
    @Test
    void dbMode_simulationRunEventWithNullEvent_isCaughtAndReported() {
        Logger.setMode(LoggerMode.DB);

        CapturedErr captured = captureErr(() ->
                Logger.logSimulationRunEvent(null, simulationRecord()));

        assertAll(
                () -> assertTrue(captured.text().contains("Failed to log simulation run event of type: null")),
                () -> assertTrue(captured.text().contains("Exception message:"))
        );
    }

    /**
     * Verifies RUN_STARTED path catches null record failures without propagating exceptions.
     */
    @Test
    void dbMode_simulationRunEventRunStartedWithNullRecord_isCaught() {
        Logger.setMode(LoggerMode.DB);
        assertDoesNotThrow(() -> Logger.logSimulationRunEvent(SimulationRunEvent.RUN_STARTED, null));
    }

    /**
     * Verifies RUN_COMPLETED path catches null record failures without propagating exceptions.
     */
    @Test
    void dbMode_simulationRunEventRunCompletedWithNullRecord_isCaught() {
        Logger.setMode(LoggerMode.DB);
        assertDoesNotThrow(() -> Logger.logSimulationRunEvent(SimulationRunEvent.RUN_COMPLETED, null));
    }

    /**
     * Verifies RUN_FAILED branch remains non-throwing (currently no-op branch).
     */
    @Test
    void dbMode_runFailedBranch_isCurrentlyNoOpAndDoesNotThrow() {
        Logger.setMode(LoggerMode.DB);

        assertDoesNotThrow(() ->
                Logger.logSimulationRunEvent(SimulationRunEvent.RUN_FAILED, simulationRecord()));
    }

    /**
     * Verifies DB mode robot events are enqueued for async batch flushing.
     */
    @Test
    void dbMode_robotEvents_areQueuedForBackgroundBatchFlush() throws Exception {
        Logger.setMode(LoggerMode.DB);
        SimLogRecord first = simLogRecord();
        SimLogRecord second = simLogRecord();
        second.setTick(2);

        Logger.logRobotEvent(RobotEvent.MOVE_INTENT, first);
        Logger.logRobotEvent(RobotEvent.COLLISION, second);

        BlockingQueue<SimLogRecord> queue = robotQueue();
        assertAll(
                () -> assertEquals(2, queue.size()),
                () -> assertTrue(queue.contains(first)),
                () -> assertTrue(queue.contains(second))
        );
    }

    /**
     * Verifies current queueing behavior ignores robot event type (including null values).
     */
    @Test
    void dbMode_robotEventTypeIsNotUsedByCurrentQueueingImplementation() throws Exception {
        Logger.setMode(LoggerMode.DB);
        SimLogRecord record = simLogRecord();

        Logger.logRobotEvent(null, record);

        assertAll(
                () -> assertEquals(1, robotQueue().size()),
                () -> assertSame(record, robotQueue().peek())
        );
    }

    /**
     * Verifies queue rejects null robot records in DB mode.
     */
    @Test
    void dbMode_robotEventWithNullRecordThrowsBecauseQueueRejectsNulls() {
        Logger.setMode(LoggerMode.DB);

        assertThrows(NullPointerException.class, () ->
                Logger.logRobotEvent(RobotEvent.MOVE_EXECUTED, null));
    }

    /**
     * Verifies explicit flush handles empty queue without side effects.
     */
    @Test
    void flushRobotEvents_withEmptyQueue_doesNotThrow() throws Exception {
        Logger.setMode(LoggerMode.DB);
        robotQueue().clear();

        assertDoesNotThrow(Logger::flushRobotEvents);
        assertTrue(robotQueue().isEmpty());
    }

    /**
     * Verifies explicit flush drains pending queue entries even if DB write fails.
     */
    @Test
    void flushRobotEvents_withPendingEntries_drainsQueue() throws Exception {
        Logger.setMode(LoggerMode.DB);
        SimLogRecord record = simLogRecord();

        Logger.logRobotEvent(RobotEvent.CHARGE_START, record);
        assertEquals(1, robotQueue().size());

        assertDoesNotThrow(Logger::flushRobotEvents);
        assertTrue(robotQueue().isEmpty());
    }

    /**
     * Verifies background worker catches batch-flush exceptions and emits stderr message.
     */
    @Test
    void backgroundWorker_nonEmptyBatch_exceptionIsCaughtAndLogged() {
        Logger.setMode(LoggerMode.DB);
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));
        try {
            Logger.logRobotEvent(RobotEvent.MOVE_EXECUTED, simLogRecord());

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
     * Reads logger static mode field via reflection.
     */
    private LoggerMode currentMode() throws Exception {
        Field mode = Logger.class.getDeclaredField("mode");
        mode.setAccessible(true);
        return (LoggerMode) mode.get(null);
    }

    /**
     * Reads logger robot-event queue via reflection.
     */
    @SuppressWarnings("unchecked")
    private BlockingQueue<SimLogRecord> robotQueue() throws Exception {
        Field queue = Logger.class.getDeclaredField("robotEventQueue");
        queue.setAccessible(true);
        return (BlockingQueue<SimLogRecord>) queue.get(null);
    }

    /**
     * Builds a representative workload-task record fixture.
     */
    private WorkloadTaskRecord workloadRecord() {
        WorkloadTaskRecord record = new WorkloadTaskRecord();
        record.setId(42L);
        record.setRunId(UUID.randomUUID());
        record.setTaskType("DELIVERY");
        record.setPriority(3);
        record.setCreatedTick(1);
        record.setAssignedTick(2);
        record.setCompletedTick(5);
        record.setPickupX(1);
        record.setPickupY(2);
        record.setDropoffX(3);
        record.setDropoffY(4);
        record.setStatus("COMPLETED");
        record.setAssignedRobotId(UUID.randomUUID());
        record.setDetails("{\"source\":\"test\"}");
        return record;
    }

    /**
     * Builds a representative simulation-run record fixture.
     */
    private SimulationRunRecord simulationRecord() {
        SimulationRunRecord record = new SimulationRunRecord();
        record.setId(UUID.randomUUID());
        record.setMapId(UUID.randomUUID());
        record.setRobotCount(2);
        record.setCoordinationPolicy("NO_OP");
        record.setRobotAlgorithms("{\"R1\":\"Greedy\"}");
        record.setWorkloadSeed(123);
        record.setWorkloadSettings("{\"maxTasks\":5}");
        record.setSimSettings("{\"maxTicks\":100}");
        record.setStartedAt(new Timestamp(1_000L));
        record.setFinishedAt(new Timestamp(2_000L));
        record.setStatus("COMPLETED");
        return record;
    }

    /**
     * Builds a representative simulation-log record fixture.
     */
    private SimLogRecord simLogRecord() {
        SimLogRecord record = new SimLogRecord();
        record.setId(7L);
        record.setRunId(UUID.randomUUID());
        record.setTick(1);
        record.setRobotId(UUID.randomUUID());
        record.setEventType("MOVE_EXECUTED");
        record.setX(4);
        record.setY(5);
        record.setDetails("{\"direction\":\"EAST\"}");
        return record;
    }

    /**
     * Captures stderr output produced while executing the provided action.
     */
    private CapturedErr captureErr(Runnable action) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream replacement = new PrintStream(output)) {
            System.setErr(replacement);
            action.run();
        } finally {
            System.setErr(originalErr);
        }
        return new CapturedErr(output.toString());
    }

    /**
     * Simple stderr capture payload.
     */
    private record CapturedErr(String text) { }
}
