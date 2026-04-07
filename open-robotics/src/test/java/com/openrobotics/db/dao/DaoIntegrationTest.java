package com.openrobotics.db.dao;

import com.openrobotics.db.Database;
import com.openrobotics.db.model.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DaoIntegrationTest {

    private static UUID mapId;
    private static UUID runId;
    private static final UUID ROBOT_ID_0 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROBOT_ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** Extra map IDs created in individual tests; cleaned up in @AfterAll. */
    private static final List<UUID> extraMapIds = new ArrayList<>();

    @BeforeAll
    static void initDatabase() throws IOException, SQLException {
        Database.init();
        Assumptions.assumeTrue(
            Database.isUuidRobotSchemaReady(),
            "Skipping DaoIntegrationTest: shared DB schema is not migrated to UUID robot columns."
        );
    }

    @AfterAll
    static void cleanup() throws SQLException {
        if (mapId != null) {
            for (SimulationRunRecord r : SimulationRunDao.findByMapId(mapId)) {
                UUID id = r.getId();
                RobotRunStatsDao.deleteByRunId(id);
                SimLogDao.deleteByRunId(id);
                WorkloadTaskDao.deleteByRunId(id);
                RunResultDao.deleteByRunId(id);
                SimulationRunDao.deleteById(id);
            }
            MapDao.deleteById(mapId);
        }
        for (UUID id : extraMapIds) {
            for (SimulationRunRecord r : SimulationRunDao.findByMapId(id)) {
                UUID runId = r.getId();
                RobotRunStatsDao.deleteByRunId(runId);
                SimLogDao.deleteByRunId(runId);
                WorkloadTaskDao.deleteByRunId(runId);
                RunResultDao.deleteByRunId(runId);
                SimulationRunDao.deleteById(runId);
            }
            MapDao.deleteById(id);
        }
    }

    // HELPER METHODS

    private static MapRecord mapRec(String name) {
        MapRecord m = new MapRecord();
        m.setName(name);
        m.setWidth(5);
        m.setHeight(5);
        m.setTileData("{}");
        m.setPreset(false);
        return m;
    }

    private static SimulationRunRecord simRun(UUID forMapId) {
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(forMapId);
        r.setRobotCount(1);
        r.setCoordinationPolicy("rr");
        r.setStatus("RUNNING");
        r.setStartedAt(Timestamp.from(Instant.now()));
        return r;
    }

    private static UUID insertRun(UUID forMapId) throws SQLException {
        return SimulationRunDao.insert(simRun(forMapId));
    }

    private static WorkloadTaskRecord task(UUID forRunId, String type) {
        WorkloadTaskRecord t = new WorkloadTaskRecord();
        t.setRunId(forRunId);
        t.setTaskType(type);
        t.setStatus("PENDING");
        return t;
    }

    private static SimLogRecord log(UUID forRunId, int tick, UUID robotId, String event) {
        SimLogRecord l = new SimLogRecord();
        l.setRunId(forRunId);
        l.setTick(tick);
        l.setRobotId(robotId);
        l.setEventType(event);
        return l;
    }

    private static RobotRunStatsRecord stats(UUID forRunId, UUID robotId) {
        RobotRunStatsRecord s = new RobotRunStatsRecord();
        s.setRunId(forRunId);
        s.setRobotId(robotId);
        s.setNavAlgorithm("ALG");
        s.setTasksCompleted(1);
        s.setDistanceTraveled(new BigDecimal("10.0"));
        s.setEnergyUsed(new BigDecimal("5.0"));
        s.setIdleTicks(0);
        s.setWaitTicks(0);
        s.setCollisions(0);
        s.setNearMisses(0);
        s.setDeadlocks(0);
        return s;
    }

    // ── MapDao ────────────────────────────────────────────────────────────────

    /** insert() with null id auto-generates UUID; inserted fields round-trip correctly. */
    @Test 
    @Order(1)
    void mapDao_insert_autoGeneratesId_andFieldsRoundTrip() throws SQLException {
        MapRecord m = mapRec("Test Warehouse");
        m.setWidth(10);
        m.setHeight(8);
        m.setTileData("{\"tiles\":[]}");

        mapId = MapDao.insert(m);
        assertNotNull(mapId);

        Optional<MapRecord> found = MapDao.findById(mapId);
        assertTrue(found.isPresent());
        assertEquals("Test Warehouse", found.get().getName());
        assertEquals(10, found.get().getWidth());
        assertEquals(8, found.get().getHeight());
        assertFalse(found.get().isPreset());
        assertNotNull(found.get().getCreatedAt());
    }

    /** insert() with an explicit id uses it; null randomSeed preserved; COALESCE fills createdAt. */
    @Test 
    @Order(2)
    void mapDao_insert_usesProvidedId_andNullRandomSeedPreserved() throws SQLException {
        UUID customId = UUID.randomUUID();
        MapRecord m = mapRec("Custom Map");
        m.setId(customId);
        m.setTileData("{\"tiles\":[{\"x\":1,\"y\":2}]}");
        m.setPreset(true);
        m.setRandomSeed(null);
        m.setCreatedAt(null);

        assertEquals(customId, MapDao.insert(m));

        Optional<MapRecord> found = MapDao.findById(customId);
        assertTrue(found.isPresent());
        assertTrue(found.get().isPreset());
        assertNull(found.get().getRandomSeed());
        assertNotNull(found.get().getCreatedAt());
        assertTrue(found.get().getTileData().contains("\"x\""));

        MapDao.deleteById(customId);
    }

    /** findById() returns empty for an unknown UUID. */
    @Test 
    @Order(3)
    void mapDao_findById_notFound() throws SQLException {
        assertTrue(MapDao.findById(UUID.randomUUID()).isEmpty());
    }

    /** findAll() returns all rows ordered by created_at DESC. */
    @Test 
    @Order(4)
    void mapDao_findAll_containsInsertedMaps_orderedByCreatedAtDesc() throws SQLException {
        MapRecord older = mapRec("Older Map");
        older.setCreatedAt(Timestamp.from(Instant.now().minusSeconds(60)));
        MapRecord newer = mapRec("Newer Map");
        newer.setCreatedAt(Timestamp.from(Instant.now()));

        UUID olderId = MapDao.insert(older);
        UUID newerId = MapDao.insert(newer);
        extraMapIds.add(olderId);
        extraMapIds.add(newerId);

        List<MapRecord> all = MapDao.findAll();
        int idxOlder = -1, idxNewer = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(olderId)) idxOlder = i;
            else if (all.get(i).getId().equals(newerId)) idxNewer = i;
        }
        assertTrue(idxOlder >= 0 && idxNewer >= 0);
        assertTrue(idxNewer < idxOlder, "newer map must appear before older map");
        assertTrue(all.stream().anyMatch(r -> r.getId().equals(mapId)));
    }

    /** deleteById() returns true for existing row, false for missing. */
    @Test
    @Order(5)
    void mapDao_deleteById_trueWhenFound_falseWhenMissing() throws SQLException {
        UUID toDelete = MapDao.insert(mapRec("To Delete"));
        assertTrue(MapDao.deleteById(toDelete));
        assertTrue(MapDao.findById(toDelete).isEmpty());
        assertFalse(MapDao.deleteById(UUID.randomUUID()));
    }

    // ── SimulationRunDao ──────────────────────────────────────────────────────

    /** insert() with null id auto-generates UUID; basic fields persist. */
    @Test
    @Order(6)
    void simulationRunDao_insert_autoGeneratesId_andFieldsRoundTrip() throws SQLException {
        SimulationRunRecord r = new SimulationRunRecord();
        r.setMapId(mapId);
        r.setRobotCount(4);
        r.setCoordinationPolicy("round-robin");
        r.setStatus("RUNNING");
        r.setStartedAt(Timestamp.from(Instant.now()));

        runId = SimulationRunDao.insert(r);
        assertNotNull(runId);

        Optional<SimulationRunRecord> found = SimulationRunDao.findById(runId);
        assertTrue(found.isPresent());
        assertEquals(4, found.get().getRobotCount());
        assertEquals("RUNNING", found.get().getStatus());
        assertEquals(mapId, found.get().getMapId());
    }

    /** insert() with explicit id uses it; all null JSONB fields stored as NULL. */
    @Test 
    @Order(7)
    void simulationRunDao_insert_usesProvidedId_andNullJsonFieldsPreserved() throws SQLException {
        UUID customId = UUID.randomUUID();
        SimulationRunRecord r = simRun(mapId);
        r.setId(customId);
        r.setStatus("QUEUED");
        r.setRobotAlgorithms(null);
        r.setWorkloadSeed(null);
        r.setWorkloadSettings(null);
        r.setSimSettings(null);
        r.setFinishedAt(null);

        assertEquals(customId, SimulationRunDao.insert(r));

        Optional<SimulationRunRecord> found = SimulationRunDao.findById(customId);
        assertTrue(found.isPresent());
        assertNull(found.get().getRobotAlgorithms());
        assertNull(found.get().getWorkloadSeed());
        assertNull(found.get().getWorkloadSettings());
        assertNull(found.get().getSimSettings());
        assertNull(found.get().getFinishedAt());
    }

    /** insert() with non-null JSONB fields and workloadSeed round-trips all values. */
    @Test 
    @Order(8)
    void simulationRunDao_insert_withAllJsonFields_roundTrip() throws SQLException {
        SimulationRunRecord r = simRun(mapId);
        r.setRobotAlgorithms("{\"0\":\"A*\",\"1\":\"D*\"}");
        r.setWorkloadSeed(42);
        r.setWorkloadSettings("{\"pattern\":\"burst\"}");
        r.setSimSettings("{\"speed\":1.5}");
        r.setStatus("QUEUED");

        UUID id = SimulationRunDao.insert(r);
        SimulationRunRecord f = SimulationRunDao.findById(id).orElseThrow();
        assertTrue(f.getRobotAlgorithms().contains("\"A*\""));
        assertEquals(42, f.getWorkloadSeed());
        assertTrue(f.getWorkloadSettings().contains("\"burst\""));
        assertTrue(f.getSimSettings().contains("\"speed\""));
    }

    /** findById() returns empty for an unknown UUID. */
    @Test 
    @Order(9)
    void simulationRunDao_findById_notFound() throws SQLException {
        assertTrue(SimulationRunDao.findById(UUID.randomUUID()).isEmpty());
    }

    /** findByMapId() filters by map and orders by started_at DESC. */
    @Test 
    @Order(10)
    void simulationRunDao_findByMapId_filteredAndOrderedByStartedAtDesc() throws SQLException {
        SimulationRunRecord r1 = simRun(mapId);
        r1.setStartedAt(Timestamp.from(Instant.now().minusSeconds(120)));
        UUID older = SimulationRunDao.insert(r1);

        SimulationRunRecord r2 = simRun(mapId);
        r2.setStartedAt(Timestamp.from(Instant.now().minusSeconds(30)));
        UUID newer = SimulationRunDao.insert(r2);

        UUID otherMapId = MapDao.insert(mapRec("Other Map"));
        extraMapIds.add(otherMapId);
        SimulationRunDao.insert(simRun(otherMapId));

        List<SimulationRunRecord> runs = SimulationRunDao.findByMapId(mapId);
        assertTrue(runs.stream().allMatch(r -> r.getMapId().equals(mapId)));

        int idxOlder = -1, idxNewer = -1;
        for (int i = 0; i < runs.size(); i++) {
            if (runs.get(i).getId().equals(older)) idxOlder = i;
            else if (runs.get(i).getId().equals(newer)) idxNewer = i;
        }
        assertTrue(idxOlder >= 0 && idxNewer >= 0);
        assertTrue(idxNewer < idxOlder, "newer run must appear first");
    }

    /** findByMapId() returns empty for an unknown map UUID. */
    @Test 
    @Order(11)
    void simulationRunDao_findByMapId_emptyForUnknownMap() throws SQLException {
        assertTrue(SimulationRunDao.findByMapId(UUID.randomUUID()).isEmpty());
    }

    /** updateStatus() sets status and finishedAt on existing run. */
    @Test 
    @Order(12)
    void simulationRunDao_updateStatus_setsStatusAndFinishedAt() throws SQLException {
        SimulationRunDao.updateStatus(runId, "COMPLETED", Timestamp.from(Instant.now()));

        SimulationRunRecord f = SimulationRunDao.findById(runId).orElseThrow();
        assertEquals("COMPLETED", f.getStatus());
        assertNotNull(f.getFinishedAt());
    }

    /** updateStatus() on a non-existent UUID is a no-op for other runs. */
    @Test 
    @Order(13)
    void simulationRunDao_updateStatus_noEffectOnUnrelatedRun() throws SQLException {
        UUID localRunId = insertRun(mapId);
        SimulationRunDao.updateStatus(UUID.randomUUID(), "FAILED", Timestamp.from(Instant.now()));

        assertEquals("RUNNING", SimulationRunDao.findById(localRunId).orElseThrow().getStatus());
    }

    /** deleteById() returns true for existing row and false for missing. */
    @Test 
    @Order(14)
    void simulationRunDao_deleteById_trueWhenFound_falseWhenMissing() throws SQLException {
        UUID toDelete = insertRun(mapId);
        assertTrue(SimulationRunDao.deleteById(toDelete));
        assertTrue(SimulationRunDao.findById(toDelete).isEmpty());
        assertFalse(SimulationRunDao.deleteById(UUID.randomUUID()));
    }

    // ── RunResultDao ──────────────────────────────────────────────────────────

    /** insert() with all non-null metrics and findByRunId() retrieve correctly. */
    @Test 
    @Order(15)
    void runResultDao_insert_andFind_withAllMetrics() throws SQLException {
        RunResultRecord r = new RunResultRecord();
        r.setRunId(runId);
        r.setCompletionTimeTicks(500);
        r.setTasksPerMinute(new BigDecimal("3.5"));
        r.setAvgDeliveryTimeTicks(new BigDecimal("12.0"));
        r.setTotalEnergy(new BigDecimal("100.0"));
        r.setEnergyPerTask(new BigDecimal("28.5"));
        r.setCollisions(2);
        r.setNearMisses(1);
        r.setDeadlockCount(0);
        r.setBatteryDeaths(0);
        r.setFairnessGini(new BigDecimal("0.25"));
        r.setExtraMetrics(null);
        RunResultDao.insert(r);

        RunResultRecord f = RunResultDao.findByRunId(runId).orElseThrow();
        assertEquals(500, f.getCompletionTimeTicks());
        assertEquals(2, f.getCollisions());
        assertEquals(new BigDecimal("3.5"), f.getTasksPerMinute());
        assertNull(f.getExtraMetrics());
    }

    /** insert() with all null optional metrics preserves NULLs. */
    @Test 
    @Order(16)
    void runResultDao_insert_andFind_withNullMetrics() throws SQLException {
        UUID localRunId = insertRun(mapId);
        RunResultRecord r = new RunResultRecord();
        r.setRunId(localRunId);
        r.setCompletionTimeTicks(1000);
        r.setTasksPerMinute(new BigDecimal("2.5"));
        r.setAvgDeliveryTimeTicks(null);
        r.setTotalEnergy(null);
        r.setEnergyPerTask(null);
        r.setCollisions(0);
        r.setNearMisses(null);
        r.setDeadlockCount(0);
        r.setBatteryDeaths(null);
        r.setFairnessGini(null);
        r.setExtraMetrics(null);
        RunResultDao.insert(r);

        RunResultRecord f = RunResultDao.findByRunId(localRunId).orElseThrow();
        assertEquals(1000, f.getCompletionTimeTicks());
        assertNull(f.getAvgDeliveryTimeTicks());
        assertNull(f.getTotalEnergy());
        assertNull(f.getEnergyPerTask());
        assertNull(f.getNearMisses());
        assertNull(f.getBatteryDeaths());
        assertNull(f.getFairnessGini());
        assertNull(f.getExtraMetrics());
    }

    /** insert() with non-null extraMetrics JSONB stores and retrieves the JSON. */
    @Test 
    @Order(17)
    void runResultDao_insert_andFind_extraMetricsJsonb() throws SQLException {
        UUID localRunId = insertRun(mapId);
        RunResultRecord r = new RunResultRecord();
        r.setRunId(localRunId);
        r.setCompletionTimeTicks(50);
        r.setTasksPerMinute(new BigDecimal("5.0"));
        r.setExtraMetrics("{\"histogram\":[1,2,3]}");
        RunResultDao.insert(r);

        String stored = RunResultDao.findByRunId(localRunId).orElseThrow().getExtraMetrics();
        assertNotNull(stored);
        assertTrue(stored.contains("\"histogram\""));
        assertTrue(stored.contains("1") && stored.contains("2") && stored.contains("3"));
    }

    /** findByRunId() returns empty for an unknown UUID. */
    @Test 
    @Order(18)
    void runResultDao_findByRunId_notFound() throws SQLException {
        assertTrue(RunResultDao.findByRunId(UUID.randomUUID()).isEmpty());
    }

    /** deleteByRunId() returns true for existing row and false for missing. */
    @Test 
    @Order(19)
    void runResultDao_deleteByRunId_trueWhenFound_falseWhenMissing() throws SQLException {
        UUID localRunId = insertRun(mapId);
        RunResultRecord r = new RunResultRecord();
        r.setRunId(localRunId);
        r.setCompletionTimeTicks(10);
        r.setTasksPerMinute(new BigDecimal("1.0"));
        RunResultDao.insert(r);

        assertTrue(RunResultDao.deleteByRunId(localRunId));
        assertTrue(RunResultDao.findByRunId(localRunId).isEmpty());
        assertFalse(RunResultDao.deleteByRunId(UUID.randomUUID()));
    }

    // ── WorkloadTaskDao ───────────────────────────────────────────────────────

    /** insert() with all fields (including non-null JSONB details) round-trips correctly. */
    @Test 
    @Order(20)
    void workloadTaskDao_insert_andFind_withFullFields_includesJsonbDetails() throws SQLException {
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
        t.setDetails("{\"note\":\"urgent\"}");

        long taskId = WorkloadTaskDao.insert(t);
        assertTrue(taskId > 0);

        WorkloadTaskRecord f = WorkloadTaskDao.findByRunId(runId).stream()
                .filter(x -> x.getId() == taskId).findFirst().orElseThrow();
        assertEquals("DELIVER", f.getTaskType());
        assertEquals(5, f.getPriority());
        assertEquals(2, f.getPickupX());
        assertEquals(8, f.getDropoffX());
        assertNotNull(f.getDetails());
        assertTrue(f.getDetails().contains("\"note\""));
    }

    /** insert() with all null optional fields stores NULLs in the DB. */
    @Test 
    @Order(21)
    void workloadTaskDao_insert_andFind_withNullOptionals() throws SQLException {
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

        long taskId = WorkloadTaskDao.insert(t);
        WorkloadTaskRecord f = WorkloadTaskDao.findByRunId(runId).stream()
                .filter(x -> x.getId() == taskId).findFirst().orElseThrow();
        assertNull(f.getPriority());
        assertNull(f.getCreatedTick());
        assertNull(f.getAssignedTick());
        assertNull(f.getCompletedTick());
        assertNull(f.getPickupX());
        assertNull(f.getPickupY());
        assertNull(f.getDropoffX());
        assertNull(f.getDropoffY());
        assertNull(f.getAssignedRobotId());
        assertNull(f.getDetails());
    }

    /** findByRunId() returns tasks ordered by id ascending. */
    @Test 
    @Order(22)
    void workloadTaskDao_findByRunId_orderedById() throws SQLException {
        UUID localRunId = insertRun(mapId);
        long id1 = WorkloadTaskDao.insert(task(localRunId, "T1"));
        long id2 = WorkloadTaskDao.insert(task(localRunId, "T2"));
        long id3 = WorkloadTaskDao.insert(task(localRunId, "T3"));

        List<WorkloadTaskRecord> tasks = WorkloadTaskDao.findByRunId(localRunId);
        assertEquals(3, tasks.size());
        assertEquals(id1, tasks.get(0).getId());
        assertEquals(id2, tasks.get(1).getId());
        assertEquals(id3, tasks.get(2).getId());
    }

    /** findByRunId() returns empty for an unknown run UUID. */
    @Test 
    @Order(23)
    void workloadTaskDao_findByRunId_emptyForUnknownRun() throws SQLException {
        assertTrue(WorkloadTaskDao.findByRunId(UUID.randomUUID()).isEmpty());
    }

    /** assignToRobot() sets assigned_robot_id, assigned_tick, and status to IN_PROGRESS. */
    @Test 
    @Order(24)
    void workloadTaskDao_assignToRobot_setsRobotIdTickAndStatus() throws SQLException {
        long taskId = WorkloadTaskDao.insert(task(runId, "DELIVER"));
        WorkloadTaskDao.assignToRobot(taskId, ROBOT_ID_1, 15);

        WorkloadTaskRecord f = WorkloadTaskDao.findByRunId(runId).stream()
                .filter(x -> x.getId() == taskId).findFirst().orElseThrow();
        assertEquals("IN_PROGRESS", f.getStatus());
        assertEquals(ROBOT_ID_1, f.getAssignedRobotId());
        assertEquals(15, f.getAssignedTick());
    }

    /** updateStatus() sets status and non-null completedTick. */
    @Test 
    @Order(25)
    void workloadTaskDao_updateStatus_withNonNullCompletedTick() throws SQLException {
        long taskId = WorkloadTaskDao.insert(task(runId, "DELIVER"));
        WorkloadTaskDao.updateStatus(taskId, "COMPLETED", 123);

        WorkloadTaskRecord f = WorkloadTaskDao.findByRunId(runId).stream()
                .filter(x -> x.getId() == taskId).findFirst().orElseThrow();
        assertEquals("COMPLETED", f.getStatus());
        assertEquals(123, f.getCompletedTick());
    }

    /** updateStatus() with null completedTick stores NULL in the DB. */
    @Test 
    @Order(26)
    void workloadTaskDao_updateStatus_withNullCompletedTick() throws SQLException {
        long taskId = WorkloadTaskDao.insert(task(runId, "DELIVER"));
        WorkloadTaskDao.updateStatus(taskId, "CANCELLED", null);

        WorkloadTaskRecord f = WorkloadTaskDao.findByRunId(runId).stream()
                .filter(x -> x.getId() == taskId).findFirst().orElseThrow();
        assertEquals("CANCELLED", f.getStatus());
        assertNull(f.getCompletedTick());
    }

    /** updateStatus() and assignToRobot() on a non-existent id are no-ops. */
    @Test 
    @Order(27)
    void workloadTaskDao_updateAndAssign_nonExistingId_noEffect() throws SQLException {
        long taskId = WorkloadTaskDao.insert(task(runId, "DELIVER"));
        WorkloadTaskDao.updateStatus(999_999L, "COMPLETED", 1);
        WorkloadTaskDao.assignToRobot(999_999L, UUID.randomUUID(), 2);

        WorkloadTaskRecord f = WorkloadTaskDao.findByRunId(runId).stream()
                .filter(x -> x.getId() == taskId).findFirst().orElseThrow();
        assertEquals("PENDING", f.getStatus());
        assertNull(f.getAssignedRobotId());
    }

    /** deleteByRunId() removes all tasks and returns count; 0 for missing run. */
    @Test 
    @Order(28)
    void workloadTaskDao_deleteByRunId_existingAndMissing() throws SQLException {
        UUID localRunId = insertRun(mapId);
        WorkloadTaskDao.insert(task(localRunId, "A"));
        WorkloadTaskDao.insert(task(localRunId, "B"));

        int deleted = WorkloadTaskDao.deleteByRunId(localRunId);
        assertTrue(deleted >= 2);
        assertTrue(WorkloadTaskDao.findByRunId(localRunId).isEmpty());
        assertEquals(0, WorkloadTaskDao.deleteByRunId(UUID.randomUUID()));
    }

    // ── SimLogDao ─────────────────────────────────────────────────────────────

    /** insert() with x/y coordinates and null details stores correctly. */
    @Test 
    @Order(29)
    void simLogDao_insert_andFind_withCoordinatesAndNullDetails() throws SQLException {
        SimLogRecord l = log(runId, 1, ROBOT_ID_0, "MOVE");
        l.setX(3);
        l.setY(4);
        l.setDetails(null);

        long id = SimLogDao.insert(l);
        assertTrue(id > 0);

        SimLogRecord f = SimLogDao.findByRunId(runId).stream()
                .filter(x -> x.getId() == id).findFirst().orElseThrow();
        assertEquals("MOVE", f.getEventType());
        assertEquals(3, f.getX());
        assertEquals(4, f.getY());
        assertNull(f.getDetails());
    }

    /** insert() with null x, y, and details stores NULLs. */
    @Test 
    @Order(30)
    void simLogDao_insert_andFind_withNullCoordinatesAndNullDetails() throws SQLException {
        SimLogRecord l = log(runId, 5, ROBOT_ID_0, "EVENT");
        l.setX(null);
        l.setY(null);
        l.setDetails(null);

        long id = SimLogDao.insert(l);
        SimLogRecord f = SimLogDao.findByRunId(runId).stream()
                .filter(x -> x.getId() == id).findFirst().orElseThrow();
        assertNull(f.getX());
        assertNull(f.getY());
        assertNull(f.getDetails());
    }

    /** insert() with non-null details JSONB covers the non-null jsonb() branch. */
    @Test 
    @Order(31)
    void simLogDao_insert_withNonNullDetails_jsonbBranchCovered() throws SQLException {
        SimLogRecord l = log(runId, 6, ROBOT_ID_0, "COLLISION");
        l.setDetails("{\"impactForce\":3.2}");

        long id = SimLogDao.insert(l);
        SimLogRecord f = SimLogDao.findByRunId(runId).stream()
                .filter(x -> x.getId() == id).findFirst().orElseThrow();
        assertNotNull(f.getDetails());
        assertTrue(f.getDetails().contains("\"impactForce\""));
    }

    /** insertBatch() with an empty list is a no-op. */
    @Test 
    @Order(32)
    void simLogDao_insertBatch_emptyList_isNoOp() throws SQLException {
        int sizeBefore = SimLogDao.findByRunId(runId).size();
        SimLogDao.insertBatch(List.of());
        assertEquals(sizeBefore, SimLogDao.findByRunId(runId).size());
    }

    /** insertBatch() with a non-empty list persists all records. */
    @Test 
    @Order(33)
    void simLogDao_insertBatch_nonEmpty_insertsAll() throws SQLException {
        UUID localRunId = insertRun(mapId);
        SimLogDao.insertBatch(List.of(
                log(localRunId, 2, ROBOT_ID_0, "PICKUP"),
                log(localRunId, 2, ROBOT_ID_1, "MOVE")
        ));

        List<SimLogRecord> tick2 = SimLogDao.findByRunIdAndTick(localRunId, 2);
        assertEquals(2, tick2.size());
    }

    /** findByRunId() orders results by tick ASC then id ASC. */
    @Test 
    @Order(34)
    void simLogDao_findByRunId_orderedByTickThenId() throws SQLException {
        UUID localRunId = insertRun(mapId);
        long id1 = SimLogDao.insert(log(localRunId, 1, ROBOT_ID_0, "A")); // tick=1
        long id2 = SimLogDao.insert(log(localRunId, 0, ROBOT_ID_0, "B")); // tick=0
        long id3 = SimLogDao.insert(log(localRunId, 1, ROBOT_ID_1, "C")); // tick=1

        List<SimLogRecord> logs = SimLogDao.findByRunId(localRunId);
        assertEquals(3, logs.size());
        assertEquals(0,   logs.get(0).getTick()); // l2 first (tick=0)
        assertEquals(1,   logs.get(1).getTick()); // l1 next  (tick=1, smaller id)
        assertEquals(1,   logs.get(2).getTick()); // l3 last  (tick=1, larger id)
        assertEquals(id2, logs.get(0).getId());
        assertEquals(id1, logs.get(1).getId());
        assertEquals(id3, logs.get(2).getId());
    }

    /** findByRunIdAndTick() returns matching records; empty for unknown run. */
    @Test 
    @Order(35)
    void simLogDao_findByRunIdAndTick_resultsAndEmpty() throws SQLException {
        List<SimLogRecord> present = SimLogDao.findByRunIdAndTick(runId, 1);
        assertFalse(present.isEmpty());
        assertTrue(present.stream().allMatch(l -> l.getTick() == 1));

        assertTrue(SimLogDao.findByRunIdAndTick(UUID.randomUUID(), 42).isEmpty());
    }

    /** deleteByRunId() removes all logs for a run; returns 0 for missing run. */
    @Test 
    @Order(36)
    void simLogDao_deleteByRunId_existingAndMissing() throws SQLException {
        UUID localRunId = insertRun(mapId);
        SimLogDao.insert(log(localRunId, 0, ROBOT_ID_0, "X"));

        int deleted = SimLogDao.deleteByRunId(localRunId);
        assertTrue(deleted >= 1);
        assertTrue(SimLogDao.findByRunId(localRunId).isEmpty());
        assertEquals(0, SimLogDao.deleteByRunId(UUID.randomUUID()));
    }

    // ── RobotRunStatsDao ──────────────────────────────────────────────────────

    /** upsert() on same (run_id, robot_id) always returns same id; last write wins. */
    @Test 
    @Order(37)
    void robotRunStatsDao_upsert_idStableAcrossMultipleUpdates() throws SQLException {
        UUID localRunId = insertRun(mapId);
        RobotRunStatsRecord s = stats(localRunId, ROBOT_ID_0);

        long id1 = RobotRunStatsDao.upsert(s);
        s.setTasksCompleted(2);
        long id2 = RobotRunStatsDao.upsert(s);
        s.setTasksCompleted(3);
        long id3 = RobotRunStatsDao.upsert(s);

        assertEquals(id1, id2);
        assertEquals(id1, id3);
        assertEquals(3, RobotRunStatsDao.findByRunIdAndRobotId(localRunId, ROBOT_ID_0).orElseThrow().getTasksCompleted());
    }

    /** upsert() with all null numeric metrics stores NULLs. */
    @Test 
    @Order(38)
    void robotRunStatsDao_upsert_withNullMetrics() throws SQLException {
        UUID localRunId = insertRun(mapId);
        RobotRunStatsRecord s = new RobotRunStatsRecord();
        s.setRunId(localRunId);
        s.setRobotId(ROBOT_ID_0);
        s.setNavAlgorithm("ALG");
        s.setTasksCompleted(null);
        s.setDistanceTraveled(null);
        s.setEnergyUsed(null);
        s.setIdleTicks(null);
        s.setWaitTicks(null);
        s.setCollisions(null);
        s.setNearMisses(null);
        s.setDeadlocks(null);
        RobotRunStatsDao.upsert(s);

        RobotRunStatsRecord f = RobotRunStatsDao.findByRunIdAndRobotId(localRunId, ROBOT_ID_0).orElseThrow();
        assertNull(f.getTasksCompleted());
        assertNull(f.getDistanceTraveled());
        assertNull(f.getEnergyUsed());
        assertNull(f.getIdleTicks());
        assertNull(f.getWaitTicks());
        assertNull(f.getCollisions());
        assertNull(f.getNearMisses());
        assertNull(f.getDeadlocks());
    }

    /** findByRunId() returns all robots ordered by robot_id; empty for unknown run. */
    @Test 
    @Order(39)
    void robotRunStatsDao_findByRunId_orderedByRobotId_andEmptyForUnknown() throws SQLException {
        UUID localRunId = insertRun(mapId);
        RobotRunStatsRecord r0 = stats(localRunId, ROBOT_ID_0);
        r0.setTasksCompleted(7);
        RobotRunStatsRecord r1 = stats(localRunId, ROBOT_ID_1);
        r1.setTasksCompleted(4);
        RobotRunStatsDao.upsert(r0);
        RobotRunStatsDao.upsert(r1);

        List<RobotRunStatsRecord> list = RobotRunStatsDao.findByRunId(localRunId);
        assertEquals(2, list.size());
        assertEquals(ROBOT_ID_0, list.get(0).getRobotId());
        assertEquals(ROBOT_ID_1, list.get(1).getRobotId());
        assertEquals(7, list.get(0).getTasksCompleted());

        assertTrue(RobotRunStatsDao.findByRunId(UUID.randomUUID()).isEmpty());
    }

    /** findByRunIdAndRobotId() returns empty for unknown run or non-existent robot. */
    @Test 
    @Order(40)
    void robotRunStatsDao_findByRunIdAndRobotId_notFound() throws SQLException {
        assertTrue(RobotRunStatsDao.findByRunIdAndRobotId(UUID.randomUUID(), ROBOT_ID_0).isEmpty());
        assertTrue(RobotRunStatsDao.findByRunIdAndRobotId(runId, UUID.randomUUID()).isEmpty());
    }

    /** deleteByRunId() removes all stats for a run; returns 0 for missing run. */
    @Test 
    @Order(41)
    void robotRunStatsDao_deleteByRunId_existingAndMissing() throws SQLException {
        UUID localRunId = insertRun(mapId);
        RobotRunStatsDao.upsert(stats(localRunId, ROBOT_ID_0));

        int deleted = RobotRunStatsDao.deleteByRunId(localRunId);
        assertTrue(deleted >= 1);
        assertTrue(RobotRunStatsDao.findByRunId(localRunId).isEmpty());
        assertEquals(0, RobotRunStatsDao.deleteByRunId(UUID.randomUUID()));
    }
}
