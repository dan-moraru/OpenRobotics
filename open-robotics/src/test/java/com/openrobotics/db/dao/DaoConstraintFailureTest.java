package com.openrobotics.db.dao;

import com.openrobotics.db.Database;
import com.openrobotics.db.model.MapRecord;
import com.openrobotics.db.model.RunResultRecord;
import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.db.model.WorkloadTaskRecord;
import com.openrobotics.db.model.RobotRunStatsRecord;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests that assert DAO writes fail when database constraints are violated.
 *
 * <p>These tests intentionally submit invalid inputs to verify that JDBC operations surface
 * {@link SQLException} for JSONB validation failures, foreign-key violations, and uniqueness
 * conflicts. The suite is guarded to run only when the shared DB schema is migrated to the UUID
 * robot-column shape expected by current DAOs.</p>
 */
public class DaoConstraintFailureTest {

    /**
     * Initializes database access once for the suite and skips tests when schema prerequisites are
     * not met in the shared environment.
     *
     * @throws IOException if database config initialization fails
     * @throws SQLException if DB bootstrap/migration connectivity fails
     */
    @BeforeAll
    static void initDatabase() throws IOException, SQLException {
        Database.init();
        Assumptions.assumeTrue(
                Database.isUuidRobotSchemaReady(),
                "Skipping DaoConstraintFailureTest: shared DB schema is not migrated to UUID robot columns."
        );
    }

    /**
     * Verifies map insertion rejects malformed JSON content in the JSONB tile-data column.
     */
    @Test
    void mapDao_insert_rejects_invalid_jsonb_tile_data() {
        MapRecord record = new MapRecord();
        record.setName("Bad JSON Map");
        record.setWidth(2);
        record.setHeight(2);
        record.setTileData("{not-valid-json");
        record.setPreset(false);

        assertThrows(SQLException.class, () -> MapDao.insert(record));
    }

    /**
     * Verifies simulation-run insert fails when referencing a non-existent map ID.
     */
    @Test
    void simulationRunDao_insert_rejects_unknown_map_foreign_key() {
        SimulationRunRecord record = new SimulationRunRecord();
        record.setMapId(UUID.randomUUID());
        record.setRobotCount(1);
        record.setCoordinationPolicy("NONE");
        record.setStartedAt(Timestamp.from(Instant.now()));
        record.setStatus("RUNNING");

        assertThrows(SQLException.class, () -> SimulationRunDao.insert(record));
    }

    /**
     * Verifies map deletion fails while a simulation run still references that map.
     *
     * <p>The test creates a valid map/run pair, asserts delete failure, then performs explicit
     * cleanup in dependency order.</p>
     *
     * @throws Exception if setup or cleanup DAO operations fail unexpectedly
     */
    @Test
    void mapDao_deleteById_rejects_removal_when_runs_still_reference_map() throws Exception {
        UUID mapId = MapDao.insert(validMap("Referenced Map"));
        UUID runId = null;
        try {
            runId = SimulationRunDao.insert(validRun(mapId));

            assertThrows(SQLException.class, () -> MapDao.deleteById(mapId));
        } finally {
            if (runId != null) {
                SimulationRunDao.deleteById(runId);
            }
            MapDao.deleteById(mapId);
        }
    }

    /**
     * Verifies run-results table enforces one result row per run (unique run reference).
     *
     * @throws Exception if setup or cleanup DAO operations fail unexpectedly
     */
    @Test
    void runResultDao_insert_rejects_duplicate_row_for_same_run() throws Exception {
        UUID mapId = MapDao.insert(validMap("Duplicate Result Map"));
        UUID runId = null;
        try {
            runId = SimulationRunDao.insert(validRun(mapId));

            RunResultRecord result = new RunResultRecord();
            result.setRunId(runId);
            result.setCompletionTimeTicks(10);
            result.setTasksPerMinute(new BigDecimal("1.0"));

            RunResultDao.insert(result);

            assertThrows(SQLException.class, () -> RunResultDao.insert(result));
        } finally {
            if (runId != null) {
                RunResultDao.deleteByRunId(runId);
                SimulationRunDao.deleteById(runId);
            }
            MapDao.deleteById(mapId);
        }
    }

    /**
     * Verifies task/log/stats DAOs reject inserts/upserts that reference unknown run IDs.
     */
    @Test
    void workloadTaskAndLogAndStats_inserts_reject_unknown_run_foreign_keys() {
        UUID unknownRunId = UUID.randomUUID();

        WorkloadTaskRecord task = new WorkloadTaskRecord();
        task.setRunId(unknownRunId);
        task.setTaskType("DELIVER");
        task.setStatus("PENDING");

        SimLogRecord log = new SimLogRecord();
        log.setRunId(unknownRunId);
        log.setTick(0);
        log.setRobotId(UUID.randomUUID());
        log.setEventType("MOVE");

        RobotRunStatsRecord stats = new RobotRunStatsRecord();
        stats.setRunId(unknownRunId);
        stats.setRobotId(UUID.randomUUID());
        stats.setNavAlgorithm("GREEDY");

        assertThrows(SQLException.class, () -> WorkloadTaskDao.insert(task));
        assertThrows(SQLException.class, () -> SimLogDao.insert(log));
        assertThrows(SQLException.class, () -> RobotRunStatsDao.upsert(stats));
    }

    /**
     * Builds a minimal valid map record used by tests that need a persisted map parent row.
     */
    private MapRecord validMap(String name) {
        MapRecord record = new MapRecord();
        record.setName(name);
        record.setWidth(2);
        record.setHeight(2);
        record.setTileData("{\"tiles\":[]}");
        record.setPreset(false);
        return record;
    }

    /**
     * Builds a minimal valid simulation-run record referencing an existing map.
     */
    private SimulationRunRecord validRun(UUID mapId) {
        SimulationRunRecord record = new SimulationRunRecord();
        record.setMapId(mapId);
        record.setRobotCount(1);
        record.setCoordinationPolicy("NONE");
        record.setStartedAt(Timestamp.from(Instant.now()));
        record.setStatus("RUNNING");
        return record;
    }
}
