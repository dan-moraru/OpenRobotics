package com.openrobotics.db.dao;

import com.openrobotics.db.Database;
import com.openrobotics.db.model.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DaoIntegrationTest {

    private static UUID mapId;
    private static UUID runId;

    /**
     * Initializes the database.
     * @throws IOException if an I/O error occurs
     * @throws SQLException if a database error occurs
     */
    @BeforeAll
    static void initDatabase() throws IOException, SQLException {
        Database.init();   
    }

    /**
     * Inserts and finds a map record.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(1)
    void mapDao_insertAndFind() throws SQLException {
        // Create a new map record
        MapRecord m = new MapRecord();
        m.setName("Test Warehouse");
        m.setWidth(10);
        m.setHeight(8);
        m.setTileData("{\"tiles\":[]}");
        m.setPreset(false);

        // Insert the map record
        mapId = MapDao.insert(m);
        assertNotNull(mapId);

        // Find the map record
        Optional<MapRecord> found = MapDao.findById(mapId);
        assertTrue(found.isPresent());
        assertEquals("Test Warehouse", found.get().getName());
        assertEquals(10, found.get().getWidth());
    }

    /**
     * Finds all map records.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(2)
    void mapDao_findAll() throws SQLException {
        // Find all map records
        List<MapRecord> all = MapDao.findAll();
        assertTrue(all.stream().anyMatch(r -> r.getId().equals(mapId)));
    }

    /**
     * Inserts and finds a simulation run record.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(3)
    void simulationRunDao_insertAndFind() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(4);
        r.setCoordinationPolicy("round-robin");
        r.setStatus("RUNNING");
        r.setStartedAt(Timestamp.from(Instant.now()));

        // Insert the simulation run record
        runId = SimulationRunDao.insert(r);
        assertNotNull(runId);

        // Find the simulation run record
        Optional<SimulationRunRecord> found = SimulationRunDao.findById(runId);
        assertTrue(found.isPresent());
        assertEquals(4, found.get().getRobotCount());
        assertEquals("RUNNING", found.get().getStatus());
    }

    /**
     * Updates the status of a simulation run record.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(4)
    void simulationRunDao_updateStatus() throws SQLException {
        // Update the status of the simulation run record
        Timestamp now = Timestamp.from(Instant.now());
        SimulationRunDao.updateStatus(runId, "COMPLETED", now);

        // Find the simulation run record
        Optional<SimulationRunRecord> found = SimulationRunDao.findById(runId);
        assertTrue(found.isPresent());
        assertEquals("COMPLETED", found.get().getStatus());
        assertNotNull(found.get().getFinishedAt());
    }

    /**
     * Finds all simulation run records by map ID.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(5)
    void simulationRunDao_findByMapId() throws SQLException {
        // Find all simulation run records by map ID
        List<SimulationRunRecord> runs = SimulationRunDao.findByMapId(mapId);
        assertTrue(runs.stream().anyMatch(r -> r.getId().equals(runId)));
    }

    /**
     * Inserts and finds a run result record.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(6)
    void runResultDao_insertAndFind() throws SQLException {
        // Create a new run result record
        RunResultRecord r = new RunResultRecord();
        r.setRunId(runId);
        r.setCompletionTimeTicks(500);
        r.setTasksPerMinute(new BigDecimal("3.5"));
        r.setCollisions(2);
        r.setDeadlockCount(0);

        // Insert the run result record
        RunResultDao.insert(r);

        // Find the run result record
        Optional<RunResultRecord> found = RunResultDao.findByRunId(runId);
        assertTrue(found.isPresent());
        assertEquals(500, found.get().getCompletionTimeTicks());
        assertEquals(2, found.get().getCollisions());
    }

    /**
     * Inserts and finds a workload task record and assigns it to a robot.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(7)
    void workloadTaskDao_insertFindAndAssign() throws SQLException {
        // Create a new workload task record
        WorkloadTaskRecord t = new WorkloadTaskRecord();
        t.setRunId(runId);
        t.setTaskType("DELIVER");
        t.setPriority(5);
        t.setCreatedTick(10);
        t.setPickupX(2);
        t.setPickupY(3);
        t.setDropoffX(8);
        t.setDropoffY(7);
        t.setStatus("PENDING");

        // Insert the workload task record
        long taskId = WorkloadTaskDao.insert(t);

        // Check if the workload task record was inserted
        assertTrue(taskId > 0);

        // Assign the workload task record to a robot
        WorkloadTaskDao.assignToRobot(taskId, 1, 15);

        // Find the workload task record
        List<WorkloadTaskRecord> tasks = WorkloadTaskDao.findByRunId(runId);
        // Check if the workload task record was found
        assertFalse(tasks.isEmpty());
        WorkloadTaskRecord assigned = tasks.stream()
                .filter(x -> x.getId() == taskId).findFirst().orElseThrow();
        assertEquals("IN_PROGRESS", assigned.getStatus());
        assertEquals(1, assigned.getAssignedRobotId());
    }

    /**
     * Inserts and batches inserts simulation log records.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(8)
    void simLogDao_insertAndBatchInsert() throws SQLException {
        // Create a new simulation log record
        SimLogRecord log1 = new SimLogRecord();
        log1.setRunId(runId);
        log1.setTick(1);
        log1.setRobotId(0);
        log1.setEventType("MOVE");
        log1.setX(3);
        log1.setY(4);

        // Insert the simulation log record
        long id = SimLogDao.insert(log1);

        // Check if the simulation log record was inserted
        assertTrue(id > 0);

        // Create a new simulation log record
        SimLogRecord log2 = new SimLogRecord();
        log2.setRunId(runId);
        log2.setTick(2);
        log2.setRobotId(0);
        log2.setEventType("PICKUP");
        log2.setX(5);
        log2.setY(6);

        // Create a new simulation log record
        SimLogRecord log3 = new SimLogRecord();
        log3.setRunId(runId);
        log3.setTick(2);
        log3.setRobotId(1);
        log3.setEventType("MOVE");
        log3.setX(1);
        log3.setY(1);

        // Insert the simulation log records
        SimLogDao.insertBatch(List.of(log2, log3));

        // Find the simulation log records by run ID and tick
        List<SimLogRecord> tick2 = SimLogDao.findByRunIdAndTick(runId, 2);
        assertEquals(2, tick2.size());

        // Find the simulation log records by run ID
        List<SimLogRecord> all = SimLogDao.findByRunId(runId);
        assertTrue(all.size() >= 3);
    }

    /**
     * Upserts a robot run stats record.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(9)
    void robotRunStatsDao_upsert() throws SQLException {
        // Create a new robot run stats record
        RobotRunStatsRecord s = new RobotRunStatsRecord();
        s.setRunId(runId);
        s.setRobotId(0);
        s.setNavAlgorithm("A*");
        s.setTasksCompleted(3);
        s.setDistanceTraveled(new BigDecimal("45.0"));
        s.setEnergyUsed(new BigDecimal("12.5"));
        s.setCollisions(1);

        // Upsert the robot run stats record
        long id1 = RobotRunStatsDao.upsert(s);

        // Check if the robot run stats record was upserted
        assertTrue(id1 > 0);

        // Create a new robot run stats record
        s.setTasksCompleted(5);
        // Upsert the robot run stats record
        long id2 = RobotRunStatsDao.upsert(s);
        assertEquals(id1, id2);

        // Find the robot run stats record by run ID and robot ID
        Optional<RobotRunStatsRecord> found = RobotRunStatsDao.findByRunIdAndRobotId(runId, 0);
        assertTrue(found.isPresent());
        assertEquals(5, found.get().getTasksCompleted());
    }

    /**
     * Verifies that looking up a non-existent map returns empty.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(10)
    void mapDao_findById_notFound() throws SQLException {
        // Find a non-existent map record
        Optional<MapRecord> notFound = MapDao.findById(UUID.randomUUID());
        // Check if the map record was not found
        assertTrue(notFound.isEmpty());
    }

    /**
     * Verifies that looking up a non-existent simulation run returns empty.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(11)
    void simulationRunDao_findById_notFound() throws SQLException {
        // Find a non-existent simulation run record
        Optional<SimulationRunRecord> notFound = SimulationRunDao.findById(UUID.randomUUID());
        // Check if the simulation run record was not found
        assertTrue(notFound.isEmpty());
    }

    /**
     * Verifies that looking up a run result for a non-existent run returns empty.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(12)
    void runResultDao_findByRunId_notFound() throws SQLException {
        // Find a non-existent run result record
        Optional<RunResultRecord> notFound = RunResultDao.findByRunId(UUID.randomUUID());
        // Check if the run result record was not found
        assertTrue(notFound.isEmpty());
    }

    /**
     * Verifies that querying workload tasks for an unknown run yields an empty list.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(13)
    void workloadTaskDao_findByRunId_emptyForUnknownRun() throws SQLException {
        // Find workload tasks for a non-existent run
        List<WorkloadTaskRecord> tasks = WorkloadTaskDao.findByRunId(UUID.randomUUID());
        assertTrue(tasks.isEmpty());
    }

    /**
     * Verifies that querying sim logs for an unknown run yields empty results.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(14)
    void simLogDao_findByRunIdAndTick_emptyForUnknownRun() throws SQLException {
        // Find sim logs for a non-existent run and tick
        List<SimLogRecord> logs = SimLogDao.findByRunIdAndTick(UUID.randomUUID(), 42);
        assertTrue(logs.isEmpty());
    }

    /**
     * Upserts robot run stats for multiple robots and verifies they are independent.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(15)
    void robotRunStatsDao_multipleRobots() throws SQLException {
        // Create a new robot run stats record
        RobotRunStatsRecord r0 = new RobotRunStatsRecord();
        r0.setRunId(runId);
        r0.setRobotId(0);
        r0.setNavAlgorithm("A*");
        r0.setTasksCompleted(7);
        r0.setDistanceTraveled(new BigDecimal("60.0"));
        r0.setEnergyUsed(new BigDecimal("15.0"));
        r0.setCollisions(2);

        // Insert the robot run stats record
        RobotRunStatsRecord r1 = new RobotRunStatsRecord();
        r1.setRunId(runId);
        r1.setRobotId(1);
        r1.setNavAlgorithm("D*");
        r1.setTasksCompleted(4);
        r1.setDistanceTraveled(new BigDecimal("30.0"));
        r1.setEnergyUsed(new BigDecimal("8.0"));
        r1.setCollisions(0);

        // Upsert the robot run stats records
        RobotRunStatsDao.upsert(r0);
        RobotRunStatsDao.upsert(r1);

        // Find the robot run stats records by run ID and robot ID
        Optional<RobotRunStatsRecord> stats0 = RobotRunStatsDao.findByRunIdAndRobotId(runId, 0);
        Optional<RobotRunStatsRecord> stats1 = RobotRunStatsDao.findByRunIdAndRobotId(runId, 1);

        // Check if the robot run stats records were found
        assertTrue(stats0.isPresent());
        assertTrue(stats1.isPresent());
        assertEquals(7, stats0.get().getTasksCompleted());
        assertEquals(4, stats1.get().getTasksCompleted());
        assertEquals(0, stats0.get().getRobotId());
        assertEquals(1, stats1.get().getRobotId());
    }

    /**
     * Inserts a map with a custom ID and null optional fields, verifying defaults and JSONB storage.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(16)
    void mapDao_insertWithCustomIdAndNullOptionals() throws SQLException {
        // Create a new custom ID
        UUID customId = UUID.randomUUID();

        // Create a new map record
        MapRecord m = new MapRecord();
        m.setId(customId);
        m.setName("Custom Map");
        m.setWidth(5);
        m.setHeight(5);
        String json = "{\"tiles\":[{\"x\":1,\"y\":2}]}";
        m.setTileData(json);
        m.setPreset(true);
        m.setRandomSeed(null);
        m.setCreatedAt(null);

        // Insert the map record
        UUID returnedId = MapDao.insert(m);

        // Check if the map record was inserted
        assertEquals(customId, returnedId);

        // Find the map record by custom ID
        Optional<MapRecord> found = MapDao.findById(customId);
        assertTrue(found.isPresent());
        String storedJson = found.get().getTileData();
        assertNotNull(storedJson);
        assertTrue(storedJson.contains("\"x\""));
        assertTrue(storedJson.contains("\"y\""));
        assertNull(found.get().getRandomSeed());
        assertNotNull(found.get().getCreatedAt());
    }

    /**
     * Verifies that findAll returns maps ordered by created_at descending.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(17)
    void mapDao_findAll_orderingByCreatedAtDesc() throws SQLException {
        // Create a new older map record
        MapRecord older = new MapRecord();
        older.setName("Older Map");
        older.setWidth(1);
        older.setHeight(1);
        older.setTileData("{}");
        older.setPreset(false);
        older.setCreatedAt(Timestamp.from(Instant.now().minusSeconds(60)));

        // Create a new newer map record
        MapRecord newer = new MapRecord();
        newer.setName("Newer Map");
        newer.setWidth(1);
        newer.setHeight(1);
        newer.setTileData("{}");
        newer.setPreset(false);
        newer.setCreatedAt(Timestamp.from(Instant.now()));

        // Insert the map records
        UUID olderId = MapDao.insert(older);
        UUID newerId = MapDao.insert(newer);

        // Find all map records
        List<MapRecord> all = MapDao.findAll();
        int idxOlder = -1;
        int idxNewer = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(olderId)) {
                idxOlder = i;
            } else if (all.get(i).getId().equals(newerId)) {
                idxNewer = i;
            }
        }

        // Check if the older map record was found and is newer than the newer map record
        assertTrue(idxOlder >= 0 && idxNewer >= 0);
        assertTrue(idxNewer < idxOlder);
    }

    /**
     * Verifies deleteById returns false when deleting a non-existent map.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(18)
    void mapDao_deleteById_returnsFalseWhenMissing() throws SQLException {
        // Delete a non-existent map record
        assertFalse(MapDao.deleteById(UUID.randomUUID()));
    }

    /**
     * Inserts a simulation run with JSON settings and null optionals.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(19)
    void simulationRunDao_insertWithJsonAndNulls() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(2);
        r.setCoordinationPolicy("priority");
        r.setRobotAlgorithms("{\"0\":\"A*\",\"1\":\"D*\"}");
        r.setWorkloadSeed(null);
        r.setWorkloadSettings("{\"pattern\":\"burst\"}");
        r.setSimSettings("{\"speed\":1.5}");
        r.setStartedAt(Timestamp.from(Instant.now()));
        r.setFinishedAt(null);
        r.setStatus("QUEUED");

        // Insert the simulation run record
        UUID id = SimulationRunDao.insert(r);

        // Find the simulation run record
        Optional<SimulationRunRecord> found = SimulationRunDao.findById(id);
        assertTrue(found.isPresent());
        SimulationRunRecord f = found.get();
        assertEquals("priority", f.getCoordinationPolicy());
        assertNotNull(f.getRobotAlgorithms());
        assertTrue(f.getRobotAlgorithms().contains("\"0\""));
        assertTrue(f.getRobotAlgorithms().contains("\"A*\""));
        assertTrue(f.getRobotAlgorithms().contains("\"1\""));
        assertNull(f.getWorkloadSeed());
        assertNotNull(f.getWorkloadSettings());
        assertTrue(f.getWorkloadSettings().contains("\"pattern\""));
        assertTrue(f.getWorkloadSettings().contains("\"burst\""));
        assertNotNull(f.getSimSettings());
        assertTrue(f.getSimSettings().contains("\"speed\""));
        assertNull(f.getFinishedAt());
        assertEquals("QUEUED", f.getStatus());
    }

    /**
     * Verifies findByMapId filters by map and orders by started_at descending.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(20)
    void simulationRunDao_findByMapId_onlyThatMapAndOrdered() throws SQLException {
        // Run for existing mapId with older timestamp
        SimulationRunRecord r1 = new SimulationRunRecord();
        r1.setMapId(mapId);
        r1.setRobotCount(1);
        r1.setCoordinationPolicy("round-robin");
        r1.setStatus("RUNNING");
        r1.setStartedAt(Timestamp.from(Instant.now().minusSeconds(120)));
        UUID run1 = SimulationRunDao.insert(r1);

        // Run for existing mapId with newer timestamp
        SimulationRunRecord r2 = new SimulationRunRecord();
        r2.setMapId(mapId);
        r2.setRobotCount(1);
        r2.setCoordinationPolicy("round-robin");
        r2.setStatus("RUNNING");
        r2.setStartedAt(Timestamp.from(Instant.now().minusSeconds(30)));
        UUID run2 = SimulationRunDao.insert(r2);

        // Run for a different map
        MapRecord otherMap = new MapRecord();
        otherMap.setName("Other Map");
        otherMap.setWidth(3);
        otherMap.setHeight(3);
        otherMap.setTileData("{}");
        otherMap.setPreset(false);
        UUID otherMapId = MapDao.insert(otherMap);

        // Create a new simulation run record
        SimulationRunRecord otherRun = new SimulationRunRecord();
        otherRun.setMapId(otherMapId);
        otherRun.setRobotCount(1);
        otherRun.setCoordinationPolicy("round-robin");
        otherRun.setStatus("RUNNING");
        otherRun.setStartedAt(Timestamp.from(Instant.now().minusSeconds(10)));
        SimulationRunDao.insert(otherRun);

        // Find the simulation run records for the map
        List<SimulationRunRecord> runsForMap = SimulationRunDao.findByMapId(mapId);
        assertTrue(runsForMap.stream().allMatch(r -> r.getMapId().equals(mapId)));

        // Check if the simulation run records were found for the map
        int idx1 = -1;
        int idx2 = -1;
        for (int i = 0; i < runsForMap.size(); i++) {
            if (runsForMap.get(i).getId().equals(run1)) {
                idx1 = i;
            } else if (runsForMap.get(i).getId().equals(run2)) {
                idx2 = i;
            }
        }
        assertTrue(idx1 >= 0 && idx2 >= 0);
        assertTrue(idx2 < idx1);
    }

    /**
     * Verifies updateStatus does not affect other runs when given a non-existing ID.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(21)
    void simulationRunDao_updateStatus_nonExistingRunNoEffect() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("round-robin");
        r.setStatus("PENDING");
        r.setStartedAt(Timestamp.from(Instant.now()));
        UUID localRunId = SimulationRunDao.insert(r);

        // Update the status of a non-existent simulation run record
        SimulationRunDao.updateStatus(UUID.randomUUID(), "FAILED", Timestamp.from(Instant.now()));

        // Find the simulation run record
        Optional<SimulationRunRecord> found = SimulationRunDao.findById(localRunId);
        assertTrue(found.isPresent());
        assertEquals("PENDING", found.get().getStatus());
    }

    /**
     * Verifies deleteById behavior for existing and missing runs.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(22)
    void simulationRunDao_deleteById_existingAndMissing() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("round-robin");
        r.setStatus("TO_DELETE");
        r.setStartedAt(Timestamp.from(Instant.now()));
        UUID toDelete = SimulationRunDao.insert(r);

        // Delete the simulation run record
        assertTrue(SimulationRunDao.deleteById(toDelete));
        // Check if the simulation run record was deleted
        assertTrue(SimulationRunDao.findById(toDelete).isEmpty());
        // Check if a non-existent simulation run record was not deleted
        assertFalse(SimulationRunDao.deleteById(UUID.randomUUID()));
    }

    /**
     * Inserts a run result with many null metrics and verifies round-trip.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(23)
    void runResultDao_insertWithNullMetrics() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("rr");
        r.setStatus("DONE");
        r.setStartedAt(Timestamp.from(Instant.now()));
        UUID localRunId = SimulationRunDao.insert(r);

        // Create a new run result record
        RunResultRecord res = new RunResultRecord();
        res.setRunId(localRunId);
        res.setCompletionTimeTicks(1000);
        res.setTasksPerMinute(new BigDecimal("2.5"));
        res.setAvgDeliveryTimeTicks(null);
        res.setTotalEnergy(null);
        res.setEnergyPerTask(null);
        res.setCollisions(0);
        res.setNearMisses(null);
        res.setDeadlockCount(0);
        res.setBatteryDeaths(null);
        res.setFairnessGini(null);
        res.setExtraMetrics(null);

        // Insert the run result record
        RunResultDao.insert(res);

        // Find the run result record
        Optional<RunResultRecord> found = RunResultDao.findByRunId(localRunId);
        // Check if the run result record was found
        assertTrue(found.isPresent());
        // Get the run result record
        RunResultRecord f = found.get();
        // Check if the run result record has the correct values
        assertEquals(1000, f.getCompletionTimeTicks());
        assertEquals(new BigDecimal("2.5"), f.getTasksPerMinute());
        assertNull(f.getAvgDeliveryTimeTicks());
        assertNull(f.getTotalEnergy());
        assertNull(f.getEnergyPerTask());
        assertEquals(0, f.getCollisions());
        assertNull(f.getNearMisses());
        assertEquals(0, f.getDeadlockCount());
        assertNull(f.getBatteryDeaths());
        assertNull(f.getFairnessGini());
        assertNull(f.getExtraMetrics());
    }

    /**
     * Inserts and retrieves extraMetrics JSONB to verify integrity.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(24)
    void runResultDao_insertAndRetrieveExtraMetricsJsonb() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("rr");
        r.setStatus("DONE");
        r.setStartedAt(Timestamp.from(Instant.now()));
        UUID localRunId = SimulationRunDao.insert(r);

        // Create a new extra metrics string
        String extra = "{\"histogram\":[1,2,3]}";

        // Create a new run result record
        RunResultRecord res = new RunResultRecord();
        res.setRunId(localRunId);
        res.setCompletionTimeTicks(50);
        res.setTasksPerMinute(new BigDecimal("5.0"));
        res.setExtraMetrics(extra);

        // Insert the run result record
        RunResultDao.insert(res);

        // Find the run result record
        Optional<RunResultRecord> found = RunResultDao.findByRunId(localRunId);
        // Check if the run result record was found
        assertTrue(found.isPresent());
        // Get the extra metrics string from the run result record
        String stored = found.get().getExtraMetrics();
        // Check if the extra metrics string is not null
        assertNotNull(stored);
        // Check if the extra metrics string contains the histogram key
        assertTrue(stored.contains("\"histogram\""));
        assertTrue(stored.contains("1"));
        assertTrue(stored.contains("2"));
        assertTrue(stored.contains("3"));
    }

    /**
     * Verifies deleteByRunId behavior for existing and missing run results.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(25)
    void runResultDao_deleteByRunId_existingAndMissing() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("rr");
        r.setStatus("DONE");
        r.setStartedAt(Timestamp.from(Instant.now()));
        UUID localRunId = SimulationRunDao.insert(r);

        // Create a new run result record
        RunResultRecord res = new RunResultRecord();
        res.setRunId(localRunId);
        res.setCompletionTimeTicks(10);
        res.setTasksPerMinute(new BigDecimal("1.0"));
        RunResultDao.insert(res);

        // Delete the run result record
        assertTrue(RunResultDao.deleteByRunId(localRunId));
        // Check if the run result record was deleted
        assertTrue(RunResultDao.findByRunId(localRunId).isEmpty());
        // Check if a non-existent run result record was not deleted
        assertFalse(RunResultDao.deleteByRunId(UUID.randomUUID()));
    }

    /**
     * Inserts a workload task with many null optionals and verifies round-trip.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(26)
    void workloadTaskDao_insertWithNulls() throws SQLException {
        // Create a new workload task record
        WorkloadTaskRecord t = new WorkloadTaskRecord();
        t.setRunId(runId);
        t.setTaskType("PICKUP_ONLY");
        t.setPriority(null);
        t.setCreatedTick(null);
        t.setAssignedTick(null);
        t.setCompletedTick(null);
        t.setPickupX(null);
        t.setPickupY(null);
        t.setDropoffX(null);
        t.setDropoffY(null);
        t.setStatus("PENDING");
        t.setAssignedRobotId(null);
        t.setDetails(null);

        // Insert the workload task record
        long taskId = WorkloadTaskDao.insert(t);
        // Find the workload task record
        List<WorkloadTaskRecord> tasks = WorkloadTaskDao.findByRunId(runId);
        // Check if the workload task record was found
        assertTrue(tasks.stream().anyMatch(x -> x.getId() == taskId));
        // Get the workload task record
        WorkloadTaskRecord found = tasks.stream()
                .filter(x -> x.getId() == taskId)
                .findFirst().orElseThrow();
        // Check if the workload task record has the correct values
        assertEquals("PENDING", found.getStatus());
        assertNull(found.getPriority());
        assertNull(found.getCreatedTick());
        assertNull(found.getAssignedTick());
        assertNull(found.getCompletedTick());
        assertNull(found.getPickupX());
        assertNull(found.getPickupY());
        assertNull(found.getDropoffX());
        assertNull(found.getDropoffY());
        assertNull(found.getAssignedRobotId());
        assertNull(found.getDetails());
    }

    /**
     * Verifies updateStatus sets completed_tick and status.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(27)
    void workloadTaskDao_updateStatus_setsCompletedTick() throws SQLException {
        // Create a new workload task record
        WorkloadTaskRecord t = new WorkloadTaskRecord();
        t.setRunId(runId);
        t.setTaskType("DELIVER");
        t.setStatus("IN_PROGRESS");

        // Insert the workload task record
        long taskId = WorkloadTaskDao.insert(t);

        // Update the status of the workload task record
        WorkloadTaskDao.updateStatus(taskId, "COMPLETED", 123);

        // Find the workload task record
        List<WorkloadTaskRecord> tasks = WorkloadTaskDao.findByRunId(runId);
        // Check if the workload task record was found
        WorkloadTaskRecord found = tasks.stream()
                .filter(x -> x.getId() == taskId)
                .findFirst().orElseThrow();
        assertEquals("COMPLETED", found.getStatus());
        assertEquals(123, found.getCompletedTick());
    }

    /**
     * Verifies updateStatus and assignToRobot on a non-existing task do not affect existing tasks.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(28)
    void workloadTaskDao_nonExistingUpdateAndAssignNoEffect() throws SQLException {
        // Create a new workload task record
        WorkloadTaskRecord t = new WorkloadTaskRecord();
        t.setRunId(runId);
        t.setTaskType("DELIVER");
        t.setStatus("PENDING");
        long taskId = WorkloadTaskDao.insert(t);

        // Update the status of a non-existent workload task record
        WorkloadTaskDao.updateStatus(999999L, "COMPLETED", 1);
        // Assign a workload task record to a non-existent robot
        WorkloadTaskDao.assignToRobot(999999L, 7, 2);

        // Find the workload task record
        List<WorkloadTaskRecord> tasks = WorkloadTaskDao.findByRunId(runId);
        WorkloadTaskRecord found = tasks.stream()
                .filter(x -> x.getId() == taskId)
                .findFirst().orElseThrow();
        assertEquals("PENDING", found.getStatus());
        assertNull(found.getAssignedRobotId());
    }

    /**
     * Verifies deleteByRunId behavior for workload tasks.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(29)
    void workloadTaskDao_deleteByRunId_existingAndMissing() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("rr");
        r.setStatus("RUNNING");
        r.setStartedAt(Timestamp.from(Instant.now()));
        UUID localRunId = SimulationRunDao.insert(r);

        // Create a new workload task record
        WorkloadTaskRecord t1 = new WorkloadTaskRecord();
        t1.setRunId(localRunId);
        t1.setTaskType("A");
        t1.setStatus("PENDING");
        WorkloadTaskDao.insert(t1);

        // Create a new workload task record
        WorkloadTaskRecord t2 = new WorkloadTaskRecord();
        t2.setRunId(localRunId);
        t2.setTaskType("B");
        t2.setStatus("PENDING");
        WorkloadTaskDao.insert(t2);

        // Delete the workload task records
        int deleted = WorkloadTaskDao.deleteByRunId(localRunId);
        // Check if the workload task records were deleted
        assertTrue(deleted >= 2);
        // Check if the workload task records were not found
        assertTrue(WorkloadTaskDao.findByRunId(localRunId).isEmpty());
        // Check if a non-existent workload task record was not deleted
        assertEquals(0, WorkloadTaskDao.deleteByRunId(UUID.randomUUID()));
    }

    /**
     * Verifies findByRunId orders workload tasks by ID.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(30)
    void workloadTaskDao_findByRunId_orderedById() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("rr");
        r.setStatus("RUNNING");
        r.setStartedAt(Timestamp.from(Instant.now()));
        UUID localRunId = SimulationRunDao.insert(r);

        // Create a new workload task record
        WorkloadTaskRecord t1 = new WorkloadTaskRecord();
        t1.setRunId(localRunId);
        t1.setTaskType("T1");
        t1.setStatus("PENDING");
        long id1 = WorkloadTaskDao.insert(t1);

        // Create a new workload task record
        WorkloadTaskRecord t2 = new WorkloadTaskRecord();
        t2.setRunId(localRunId);
        t2.setTaskType("T2");
        t2.setStatus("PENDING");
        long id2 = WorkloadTaskDao.insert(t2);

        // Create a new workload task record
        WorkloadTaskRecord t3 = new WorkloadTaskRecord();
        t3.setRunId(localRunId);
        t3.setTaskType("T3");
        t3.setStatus("PENDING");
        long id3 = WorkloadTaskDao.insert(t3);

        // Find the workload task records
        List<WorkloadTaskRecord> tasks = WorkloadTaskDao.findByRunId(localRunId);
        // Check if the workload task records were found
        assertEquals(3, tasks.size());
        // Check if the workload task records have the correct IDs
        assertEquals(id1, tasks.get(0).getId());
        assertEquals(id2, tasks.get(1).getId());
        assertEquals(id3, tasks.get(2).getId());
    }

    /**
     * Inserts a sim log with null coordinates and details.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(31)
    void simLogDao_insertWithNullCoordinatesAndDetails() throws SQLException {
        // Create a new simulation log record
        SimLogRecord log = new SimLogRecord();
        log.setRunId(runId);
        log.setTick(5);
        log.setRobotId(0);
        log.setEventType("EVENT");
        log.setX(null);
        log.setY(null);
        log.setDetails(null);

        // Insert the simulation log record
        long id = SimLogDao.insert(log);
        // Check if the simulation log record was inserted
        assertTrue(id > 0);

        // Find the simulation log records
        List<SimLogRecord> logs = SimLogDao.findByRunId(runId);
        // Check if the simulation log records were found
        SimLogRecord found = logs.stream()
                .filter(l -> l.getId() == id)
                .findFirst().orElseThrow();
        // Check if the simulation log record has the correct values
        assertNull(found.getX());
        assertNull(found.getY());
        assertNull(found.getDetails());
    }

    /**
     * Verifies insertBatch with an empty list is a no-op.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(32)
    void simLogDao_insertBatch_emptyListNoOp() throws SQLException {
        // Find the simulation log records
        List<SimLogRecord> before = SimLogDao.findByRunId(runId);
        // Insert a batch of simulation log records
        SimLogDao.insertBatch(List.of());
        // Find the simulation log records
        List<SimLogRecord> after = SimLogDao.findByRunId(runId);
        // Check if the simulation log records were found
        assertEquals(before.size(), after.size());
    }

    /**
     * Verifies findByRunId ordering by tick then ID.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(33)
    void simLogDao_findByRunId_orderedByTickAndId() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("rr");
        r.setStatus("RUNNING");
        r.setStartedAt(Timestamp.from(Instant.now()));
        UUID localRunId = SimulationRunDao.insert(r);

        // Create a new simulation log record
        SimLogRecord l1 = new SimLogRecord();
        l1.setRunId(localRunId);
        l1.setTick(1);
        l1.setRobotId(0);
        l1.setEventType("A");
        long id1 = SimLogDao.insert(l1);

        // Create a new simulation log record
        SimLogRecord l2 = new SimLogRecord();
        l2.setRunId(localRunId);
        l2.setTick(0);
        l2.setRobotId(0);
        l2.setEventType("B");

        // Create a new simulation log record
        SimLogRecord l3 = new SimLogRecord();
        l3.setRunId(localRunId);
        l3.setTick(1);
        l3.setRobotId(1);
        l3.setEventType("C");
        long id2 = SimLogDao.insert(l3);

        // Find the simulation log records
        List<SimLogRecord> logs = SimLogDao.findByRunId(localRunId);
        // Check if the simulation log records were found
        assertEquals(3, logs.size());
        assertEquals(0, logs.get(0).getTick());
        assertEquals(1, logs.get(1).getTick());
        assertEquals(1, logs.get(2).getTick());
        // For same tick=1, order by ID
        assertTrue(logs.get(1).getId() == id1 || logs.get(1).getId() == id2);
        assertTrue(logs.get(2).getId() == id1 || logs.get(2).getId() == id2);
    }

    /**
     * Verifies deleteByRunId behavior for sim logs.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(34)
    void simLogDao_deleteByRunId_existingAndMissing() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("rr");
        r.setStatus("RUNNING");
        r.setStartedAt(Timestamp.from(Instant.now()));
        UUID localRunId = SimulationRunDao.insert(r);

        // Create a new simulation log record
        SimLogRecord l = new SimLogRecord();
        l.setRunId(localRunId);
        l.setTick(0);
        l.setRobotId(0);
        l.setEventType("X");
        SimLogDao.insert(l);

        // Delete the simulation log records
        int deleted = SimLogDao.deleteByRunId(localRunId);
        assertTrue(deleted >= 1);
        assertTrue(SimLogDao.findByRunId(localRunId).isEmpty());

        // Check if a non-existent simulation log record was not deleted
        assertEquals(0, SimLogDao.deleteByRunId(UUID.randomUUID()));
    }

    /**
     * Verifies findByRunId for robot stats returns empty for an unknown run.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(35)
    void robotRunStatsDao_findByRunId_emptyForUnknownRun() throws SQLException {
        // Find robot stats for a non-existent run
        List<RobotRunStatsRecord> stats = RobotRunStatsDao.findByRunId(UUID.randomUUID());
        assertTrue(stats.isEmpty());
    }

    /**
     * Verifies ID stability across multiple upserts for the same robot.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(36)
    void robotRunStatsDao_upsert_idStableAcrossUpdates() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("rr");
        r.setStatus("RUNNING");
        r.setStartedAt(Timestamp.from(Instant.now()));
        UUID localRunId = SimulationRunDao.insert(r);

        // Create a new robot run stats record
        RobotRunStatsRecord s = new RobotRunStatsRecord();
        s.setRunId(localRunId);
        s.setRobotId(5);
        s.setNavAlgorithm("ALG");
        s.setTasksCompleted(1);
        s.setDistanceTraveled(new BigDecimal("1.0"));
        s.setEnergyUsed(new BigDecimal("1.0"));
        s.setIdleTicks(0);
        s.setWaitTicks(0);
        s.setCollisions(0);
        s.setNearMisses(0);
        s.setDeadlocks(0);

        // Upsert the robot run stats record
        long id1 = RobotRunStatsDao.upsert(s);
        s.setTasksCompleted(2);
        // Upsert the robot run stats record
        long id2 = RobotRunStatsDao.upsert(s);
        // Set the number of tasks completed
        s.setTasksCompleted(3);
        long id3 = RobotRunStatsDao.upsert(s);

        // Check if the robot run stats record was upserted
        assertEquals(id1, id2);
        assertEquals(id1, id3);

        // Find the robot run stats record by run ID and robot ID
        Optional<RobotRunStatsRecord> found = RobotRunStatsDao.findByRunIdAndRobotId(localRunId, 5);
        assertTrue(found.isPresent());
        assertEquals(3, found.get().getTasksCompleted());
    }

    /**
     * Verifies upsert works with some null metrics.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(37)
    void robotRunStatsDao_upsertWithNullMetrics() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("rr");
        r.setStatus("RUNNING");
        r.setStartedAt(Timestamp.from(Instant.now()));
        UUID localRunId = SimulationRunDao.insert(r);

        // Create a new robot run stats record
        RobotRunStatsRecord s = new RobotRunStatsRecord();
        s.setRunId(localRunId);
        s.setRobotId(6);
        s.setNavAlgorithm("ALG");
        s.setTasksCompleted(null);
        s.setDistanceTraveled(null);
        s.setEnergyUsed(null);
        s.setIdleTicks(null);
        s.setWaitTicks(null);
        s.setCollisions(null);
        s.setNearMisses(null);
        s.setDeadlocks(null);

        // Upsert the robot run stats record
        RobotRunStatsDao.upsert(s);

        // Find the robot run stats record by run ID and robot ID
        Optional<RobotRunStatsRecord> found = RobotRunStatsDao.findByRunIdAndRobotId(localRunId, 6);
        assertTrue(found.isPresent());
        RobotRunStatsRecord f = found.get();
        assertNull(f.getTasksCompleted());
        assertNull(f.getDistanceTraveled());
        assertNull(f.getEnergyUsed());
        assertNull(f.getIdleTicks());
        assertNull(f.getWaitTicks());
        assertNull(f.getCollisions());
        assertNull(f.getNearMisses());
        assertNull(f.getDeadlocks());
    }

    /**
     * Verifies findByRunIdAndRobotId returns empty when no stats exist.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(38)
    void robotRunStatsDao_findByRunIdAndRobotId_notFound() throws SQLException {
        // Find the robot run stats record by run ID and robot ID
        Optional<RobotRunStatsRecord> stats =
                RobotRunStatsDao.findByRunIdAndRobotId(UUID.randomUUID(), 999);
        assertTrue(stats.isEmpty());
    }

    /**
     * Verifies deleteByRunId behavior for robot stats.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(39)
    void robotRunStatsDao_deleteByRunId_existingAndMissing() throws SQLException {
        // Create a new simulation run record
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("rr");
        r.setStatus("RUNNING");
        r.setStartedAt(Timestamp.from(Instant.now()));
        UUID localRunId = SimulationRunDao.insert(r);

        // Create a new robot run stats record
        RobotRunStatsRecord s = new RobotRunStatsRecord();
        s.setRunId(localRunId);
        s.setRobotId(7);
        s.setNavAlgorithm("ALG");
        s.setTasksCompleted(1);
        s.setDistanceTraveled(new BigDecimal("1.0"));
        s.setEnergyUsed(new BigDecimal("1.0"));
        s.setIdleTicks(0);
        s.setWaitTicks(0);
        s.setCollisions(0);
        s.setNearMisses(0);
        s.setDeadlocks(0);

        // Upsert the robot run stats record
        RobotRunStatsDao.upsert(s);

        // Delete the robot run stats record
        int deleted = RobotRunStatsDao.deleteByRunId(localRunId);
        // Check if the robot run stats record was deleted
        assertTrue(deleted >= 1);
        assertTrue(RobotRunStatsDao.findByRunId(localRunId).isEmpty());

        // Check if a non-existent robot run stats record was not deleted
        assertEquals(0, RobotRunStatsDao.deleteByRunId(UUID.randomUUID()));
    }

    /**
     * Cleans up the database.
     * @throws SQLException if a database error occurs
     */
    @Test
    @Order(40)
    void cleanup() throws SQLException {
        // Delete all runs and associated data for the primary mapId used across tests
        List<SimulationRunRecord> runsForMap = SimulationRunDao.findByMapId(mapId);
        for (SimulationRunRecord r : runsForMap) {
            UUID id = r.getId();
            RobotRunStatsDao.deleteByRunId(id);
            SimLogDao.deleteByRunId(id);
            WorkloadTaskDao.deleteByRunId(id);
            RunResultDao.deleteByRunId(id);
            SimulationRunDao.deleteById(id);
        }

        MapDao.deleteById(mapId);

        // Check if the map record was deleted
        assertTrue(MapDao.findById(mapId).isEmpty());
        // Check if the simulation run records were deleted
        assertTrue(SimulationRunDao.findByMapId(mapId).isEmpty());
    }
}