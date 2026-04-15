package com.openrobotics.integration.db;

import com.openrobotics.AppState;
import com.openrobotics.db.Database;
import com.openrobotics.db.dao.MapDao;
import com.openrobotics.db.dao.RobotRunStatsDao;
import com.openrobotics.db.dao.RunResultDao;
import com.openrobotics.db.dao.SimLogDao;
import com.openrobotics.db.dao.SimulationRunDao;
import com.openrobotics.db.dao.WorkloadTaskDao;
import com.openrobotics.db.model.MapRecord;
import com.openrobotics.db.model.RobotRunStatsRecord;
import com.openrobotics.db.model.RunResultRecord;
import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.integration.SimulationIntegrationTestSupport;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.LoggerMode;
import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.Task;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for end-to-end simulation persistence into DAO-backed tables.
 *
 * <p>This suite validates that completed simulation state can be materialized across map/run/task/
 * log/stats/result DAOs and that deleting a run removes dependent persisted records.</p>
 */
public class SimulationPersistenceIntegrationTest extends SimulationIntegrationTestSupport {

    /**
     * Tracks map row created by the current test for dependency-safe cleanup.
     */
    private UUID createdMapId;
    /**
     * Tracks run row created by the current test for dependency-safe cleanup.
     */
    private UUID createdRunId;

    /**
     * Seeds {@link AppState} with a minimal engine so code paths expecting an active engine (for
     * example logging helpers) remain valid during integration tests.
     */
    private void seedAppState() {
        SimulationEngine dummyEngine = new SimulationEngine(
                new Map(1, 1),
                new Robot[0],
                new Dispatcher(),
                CoordinationPolicy.noOp()
        );

        AppState.setEngine(dummyEngine);
    }

    /**
     * Prepares shared state before each test and disables runtime logger side effects.
     */
    @BeforeEach
    public void setUp() {
        seedAppState();
        Logger.setMode(LoggerMode.NO_OP); // disable logging during tests
    }

    /**
     * Initializes database access and skips suite when UUID robot schema is not available.
     *
     * @throws IOException if DB bootstrap fails
     * @throws SQLException if DB connectivity/migration checks fail
     */
    @BeforeAll
    static void initDatabase() throws IOException, SQLException {
        Database.init();
        Assumptions.assumeTrue(
                Database.isUuidRobotSchemaReady(),
                "Skipping SimulationPersistenceIntegrationTest: shared DB schema is not migrated to UUID robot columns."
        );
    }

    /**
     * Cleans persisted rows created by tests in child-first dependency order.
     *
     * @throws SQLException if DAO cleanup operations fail
     */
    @AfterEach
    void cleanup() throws SQLException {
        if (createdRunId != null) {
            RobotRunStatsDao.deleteByRunId(createdRunId);
            SimLogDao.deleteByRunId(createdRunId);
            WorkloadTaskDao.deleteByRunId(createdRunId);
            RunResultDao.deleteByRunId(createdRunId);
            SimulationRunDao.deleteById(createdRunId);
            createdRunId = null;
        }

        if (createdMapId != null) {
            MapDao.deleteById(createdMapId);
            createdMapId = null;
        }
    }

    /**
     * Verifies a completed simulation snapshot can be persisted and queried consistently across all
     * result-oriented DAOs.
     *
     * @throws Exception if simulation setup or DAO operations fail
     */
    @Test
    void completed_simulation_state_persists_cleanly_across_all_result_daos() throws Exception {
        Map map = new Map(5, 1);
        Robot robot = greedyRobot("PersistBot", 0, 0, 88L);
        map.addEntity(robot);

        Dispatcher dispatcher = new Dispatcher();
        Task task = new Task(1, pos(1, 0), pos(2, 0), 10);
        dispatcher.addTask(task);

        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[]{robot},
                dispatcher,
                CoordinationPolicy.noOp()
        );
        runTicks(engine, 4);

        createdMapId = MapDao.insert(mapRecord("Persistence Map", map));
        createdRunId = SimulationRunDao.insert(runRecord(createdMapId, engine));

        long taskId = WorkloadTaskDao.insert(workloadTaskRecord(createdRunId, task, robot, engine));
        SimLogDao.insertBatch(List.of(
                simLogRecord(createdRunId, 0, robot.getId(), "TASK_ASSIGNED", 0, 0, null),
                simLogRecord(createdRunId, engine.getTickCounter(), robot.getId(), "TASK_COMPLETED", 2, 0, "{\"taskId\":1}")
        ));
        long statsId = RobotRunStatsDao.upsert(robotStatsRecord(createdRunId, robot));
        RunResultDao.insert(runResultRecord(createdRunId, engine, robot));

        assertEquals("Persistence Map", MapDao.findById(createdMapId).orElseThrow().getName());

        SimulationRunRecord runRecord = SimulationRunDao.findById(createdRunId).orElseThrow();
        assertEquals(1, runRecord.getRobotCount());
        assertEquals("COMPLETED", runRecord.getStatus());
        assertTrue(runRecord.getRobotAlgorithms().contains("GREEDY"));

        WorkloadTaskRecord storedTask = WorkloadTaskDao.findByRunId(createdRunId).stream()
                .filter(record -> record.getId() == taskId)
                .findFirst()
                .orElseThrow();
        assertEquals("COMPLETED", storedTask.getStatus());
        assertEquals(robot.getId(), storedTask.getAssignedRobotId());
        assertEquals(engine.getTickCounter(), storedTask.getCompletedTick());

        List<SimLogRecord> logs = SimLogDao.findByRunId(createdRunId);
        assertEquals(2, logs.size());
        assertEquals("TASK_ASSIGNED", logs.get(0).getEventType());
        assertEquals("TASK_COMPLETED", logs.get(1).getEventType());

        RobotRunStatsRecord stats = RobotRunStatsDao.findByRunIdAndRobotId(createdRunId, robot.getId()).orElseThrow();
        assertEquals(statsId, stats.getId());
        assertEquals(robot.getTasksCompleted(), stats.getTasksCompleted());
        assertEquals(BigDecimal.valueOf(robot.getTotalDistanceMoved()).setScale(1), stats.getDistanceTraveled());

        RunResultRecord result = RunResultDao.findByRunId(createdRunId).orElseThrow();
        assertEquals(engine.getTickCounter(), result.getCompletionTimeTicks());
        assertEquals(BigDecimal.valueOf(robot.getTotalEnergyConsumed()).setScale(1), result.getTotalEnergy());
        assertEquals(0, result.getCollisions());
    }

    /**
     * Verifies deleting a run removes associated persisted results, tasks, logs, and stats.
     *
     * @throws Exception if DAO setup or deletion fails
     */
    @Test
    void deleting_a_simulation_run_cascades_persisted_results_logs_tasks_and_stats() throws Exception {
        createdMapId = MapDao.insert(mapRecord("Cascade Map", new Map(2, 1)));
        createdRunId = SimulationRunDao.insert(runRecord(createdMapId, null));
        UUID deletedRunId = createdRunId;

        UUID robotId = UUID.randomUUID();
        WorkloadTaskDao.insert(workloadTaskRecord(createdRunId, new Task(1, pos(0, 0), pos(1, 0), 1), null, null));
        SimLogDao.insert(simLogRecord(createdRunId, 0, robotId, "START", 0, 0, null));
        RobotRunStatsDao.upsert(robotStatsRecord(createdRunId, statsRobot(robotId)));
        RunResultDao.insert(runResultRecord(createdRunId, 4, 1.0, 1.0));

        assertTrue(SimulationRunDao.deleteById(createdRunId));
        createdRunId = null;

        assertTrue(RunResultDao.findByRunId(deletedRunId).isEmpty());
        assertTrue(WorkloadTaskDao.findByRunId(deletedRunId).isEmpty());
        assertTrue(SimLogDao.findByRunId(deletedRunId).isEmpty());
        assertTrue(RobotRunStatsDao.findByRunId(deletedRunId).isEmpty());
    }

    /**
     * Creates a minimal map record fixture from a runtime map instance.
     */
    private MapRecord mapRecord(String name, Map map) {
        MapRecord record = new MapRecord();
        record.setName(name);
        record.setWidth(map.getWidth());
        record.setHeight(map.getHeight());
        record.setTileData("{\"tiles\":[]}");
        record.setPreset(false);
        return record;
    }

    /**
     * Creates a simulation-run record fixture linked to a map and optional runtime engine.
     */
    private SimulationRunRecord runRecord(UUID mapId, SimulationEngine engine) {
        SimulationRunRecord record = new SimulationRunRecord();
        record.setMapId(mapId);
        record.setRobotCount(engine != null && engine.getRobots() != null ? engine.getRobots().length : 1);
        record.setCoordinationPolicy("NONE");
        record.setRobotAlgorithms("{\"PersistBot\":\"GREEDY\"}");
        record.setSimSettings("{\"source\":\"integration-test\"}");
        record.setStartedAt(Timestamp.from(Instant.now()));
        record.setFinishedAt(Timestamp.from(Instant.now()));
        record.setStatus("COMPLETED");
        return record;
    }

    /**
     * Creates a persisted-task fixture from runtime task and optional robot/engine context.
     */
    private WorkloadTaskRecord workloadTaskRecord(UUID runId, Task task, Robot robot, SimulationEngine engine) {
        WorkloadTaskRecord record = new WorkloadTaskRecord();
        record.setRunId(runId);
        record.setTaskType("DELIVERY");
        record.setPriority(task.getPriority());
        record.setCreatedTick(0);
        record.setAssignedTick(0);
        record.setCompletedTick(engine != null ? engine.getTickCounter() : null);
        record.setPickupX(task.getPickupLocation().getX());
        record.setPickupY(task.getPickupLocation().getY());
        record.setDropoffX(task.getDropoffLocation().getX());
        record.setDropoffY(task.getDropoffLocation().getY());
        record.setStatus(engine != null ? "COMPLETED" : "PENDING");
        record.setAssignedRobotId(robot != null ? robot.getId() : null);
        record.setDetails("{\"taskId\":" + task.getId() + "}");
        return record;
    }

    /**
     * Creates a simulation-log fixture row.
     */
    private SimLogRecord simLogRecord(UUID runId, int tick, UUID robotId, String eventType, Integer x, Integer y, String details) {
        SimLogRecord record = new SimLogRecord();
        record.setRunId(runId);
        record.setTick(tick);
        record.setRobotId(robotId);
        record.setEventType(eventType);
        record.setX(x);
        record.setY(y);
        record.setDetails(details);
        return record;
    }

    /**
     * Creates robot-run stats fixture from runtime robot metrics.
     */
    private RobotRunStatsRecord robotStatsRecord(UUID runId, Robot robot) {
        RobotRunStatsRecord record = new RobotRunStatsRecord();
        record.setRunId(runId);
        record.setRobotId(robot.getId());
        record.setNavAlgorithm(robot.getNav().toString());
        record.setTasksCompleted(robot.getTasksCompleted());
        record.setDistanceTraveled(BigDecimal.valueOf(robot.getTotalDistanceMoved()).setScale(1));
        record.setEnergyUsed(BigDecimal.valueOf(robot.getTotalEnergyConsumed()).setScale(1));
        record.setIdleTicks(robot.getTotalIdleTicks());
        record.setWaitTicks(robot.getStuckTicks());
        record.setCollisions(0);
        record.setNearMisses(0);
        record.setDeadlocks(0);
        return record;
    }

    /**
     * Creates a run-result fixture from runtime simulation/robot metrics.
     */
    private RunResultRecord runResultRecord(UUID runId, SimulationEngine engine, Robot robot) {
        RunResultRecord record = new RunResultRecord();
        record.setRunId(runId);
        record.setCompletionTimeTicks(engine.getTickCounter());
        record.setTasksPerMinute(BigDecimal.valueOf(15.0).setScale(1));
        record.setAvgDeliveryTimeTicks(BigDecimal.valueOf(engine.getTickCounter()).setScale(1));
        record.setTotalEnergy(BigDecimal.valueOf(robot.getTotalEnergyConsumed()).setScale(1));
        record.setEnergyPerTask(BigDecimal.valueOf(robot.getTotalEnergyConsumed()).setScale(1));
        record.setCollisions(0);
        record.setNearMisses(0);
        record.setDeadlockCount(0);
        record.setBatteryDeaths(0);
        record.setFairnessGini(BigDecimal.ZERO.setScale(1));
        record.setExtraMetrics("{\"tasksCompleted\":" + robot.getTasksCompleted() + "}");
        return record;
    }

    /**
     * Creates a minimal run-result fixture with explicit scalar values.
     */
    private RunResultRecord runResultRecord(UUID runId, int completionTicks, double totalEnergy, double tasksPerMinute) {
        RunResultRecord record = new RunResultRecord();
        record.setRunId(runId);
        record.setCompletionTimeTicks(completionTicks);
        record.setTasksPerMinute(BigDecimal.valueOf(tasksPerMinute).setScale(1));
        record.setTotalEnergy(BigDecimal.valueOf(totalEnergy).setScale(1));
        return record;
    }

    /**
     * Creates a deterministic robot fixture used for stats persistence tests.
     */
    private Robot statsRobot(UUID robotId) {
        Robot robot = greedyRobot(robotId, "Persisted", 0, 0, 1L);
        robot.setCurrentTask(null);
        return robot;
    }
}
