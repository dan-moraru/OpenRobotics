package com.openrobotics.logging;

import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.db.model.WorkloadTaskRecord;
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

class LoggerTest {

    @BeforeEach
    void setUp() throws Exception {
        Logger.setMode(LoggerMode.NO_OP);
        robotQueue().clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        Logger.setMode(LoggerMode.NO_OP);
        robotQueue().clear();
    }

    @Test
    void constructor_is_private_but_reflection_can_instantiate_for_coverage() throws Exception {
        Constructor<Logger> constructor = Logger.class.getDeclaredConstructor();

        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);

        assertNotNull(constructor.newInstance());
    }

    @Test
    void setMode_updates_static_logger_mode() throws Exception {
        Logger.setMode(LoggerMode.DB);
        assertSame(LoggerMode.DB, currentMode());

        Logger.setMode(LoggerMode.NO_OP);
        assertSame(LoggerMode.NO_OP, currentMode());
    }

    @Test
    void noOpMode_ignores_task_events_and_returns_minus_one_without_touching_record() {
        WorkloadTaskRecord record = workloadRecord();

        long result = Logger.logTaskEvent(TaskEvent.TASK_CREATED, record);

        assertEquals(-1, result);
    }

    @Test
    void noOpMode_ignores_null_task_event_and_null_record_safely() {
        long result = assertDoesNotThrow(() -> Logger.logTaskEvent(null, null));

        assertEquals(-1, result);
    }

    @Test
    void noOpMode_ignores_simulation_run_events_including_nulls() {
        assertAll(
                () -> assertDoesNotThrow(() -> Logger.logSimulationRunEvent(SimulationRunEvent.RUN_STARTED, simulationRecord())),
                () -> assertDoesNotThrow(() -> Logger.logSimulationRunEvent(null, null))
        );
    }

    @Test
    void noOpMode_ignores_robot_events_and_does_not_enqueue_records() throws Exception {
        SimLogRecord record = simLogRecord();

        Logger.logRobotEvent(RobotEvent.MOVE_EXECUTED, record);
        Logger.logRobotEvent(null, null);

        assertTrue(robotQueue().isEmpty());
    }

    @Test
    void dbMode_taskEventWithNullEvent_isCaughtAndReported() {
        Logger.setMode(LoggerMode.DB);

        CapturedErr captured = captureErr(() -> {
            long result = Logger.logTaskEvent(null, workloadRecord());
            assertEquals(-1, result);
        });

        assertAll(
                () -> assertTrue(captured.text().contains("Failed to log task event of type: null")),
                () -> assertTrue(captured.text().contains("Exception message:"))
        );
    }

    @Test
    void dbMode_taskAssignedWithMissingId_isCaughtBeforeDatabaseCall() {
        Logger.setMode(LoggerMode.DB);
        WorkloadTaskRecord record = workloadRecord();
        record.setId(null);

        CapturedErr captured = captureErr(() -> {
            long result = Logger.logTaskEvent(TaskEvent.TASK_ASSIGNED, record);
            assertEquals(-1, result);
        });

        assertTrue(captured.text().contains("Failed to log task event of type: TASK_ASSIGNED"));
    }

    @Test
    void dbMode_taskCompletedWithMissingId_isCaughtBeforeDatabaseCall() {
        Logger.setMode(LoggerMode.DB);
        WorkloadTaskRecord record = workloadRecord();
        record.setId(null);

        CapturedErr captured = captureErr(() -> {
            long result = Logger.logTaskEvent(TaskEvent.TASK_COMPLETED, record);
            assertEquals(-1, result);
        });

        assertTrue(captured.text().contains("Failed to log task event of type: TASK_COMPLETED"));
    }

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

    @Test
    void dbMode_runFailedBranch_isCurrentlyNoOpAndDoesNotThrow() {
        Logger.setMode(LoggerMode.DB);

        assertDoesNotThrow(() ->
                Logger.logSimulationRunEvent(SimulationRunEvent.RUN_FAILED, simulationRecord()));
    }

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

    @Test
    void dbMode_robotEventWithNullRecordThrowsBecauseQueueRejectsNulls() {
        Logger.setMode(LoggerMode.DB);

        assertThrows(NullPointerException.class, () ->
                Logger.logRobotEvent(RobotEvent.MOVE_EXECUTED, null));
    }

    @Test
    void settingNullModeMakesTaskLoggingFailSafelyButRobotLoggingStillQueues() throws Exception {
        Logger.setMode(null);
        SimLogRecord record = simLogRecord();
        long[] result = new long[1];

        CapturedErr captured = captureErr(() ->
                result[0] = Logger.logTaskEvent(TaskEvent.TASK_ASSIGNED, workloadRecord()));
        Logger.logRobotEvent(RobotEvent.NEAR_MISS, record);

        assertAll(
                () -> assertEquals(-1, result[0]),
                () -> assertTrue(captured.text().contains("Failed to log task event of type: TASK_ASSIGNED")),
                () -> assertEquals(1, robotQueue().size()),
                () -> assertSame(record, robotQueue().peek())
        );
    }

    private LoggerMode currentMode() throws Exception {
        Field mode = Logger.class.getDeclaredField("mode");
        mode.setAccessible(true);
        return (LoggerMode) mode.get(null);
    }

    @SuppressWarnings("unchecked")
    private BlockingQueue<SimLogRecord> robotQueue() throws Exception {
        Field queue = Logger.class.getDeclaredField("robotEventQueue");
        queue.setAccessible(true);
        return (BlockingQueue<SimLogRecord>) queue.get(null);
    }

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

    private record CapturedErr(String text) { }
}
