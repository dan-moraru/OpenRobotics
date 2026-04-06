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

public class DaoConstraintFailureTest {

    @BeforeAll
    static void initDatabase() throws IOException, SQLException {
        Database.init();
        Assumptions.assumeTrue(
                Database.isUuidRobotSchemaReady(),
                "Skipping DaoConstraintFailureTest: shared DB schema is not migrated to UUID robot columns."
        );
    }

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

    private MapRecord validMap(String name) {
        MapRecord record = new MapRecord();
        record.setName(name);
        record.setWidth(2);
        record.setHeight(2);
        record.setTileData("{\"tiles\":[]}");
        record.setPreset(false);
        return record;
    }

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
