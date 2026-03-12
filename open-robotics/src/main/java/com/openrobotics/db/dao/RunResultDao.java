package com.openrobotics.db.dao;

import com.openrobotics.db.Database;
import com.openrobotics.db.model.RunResultRecord;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public final class RunResultDao {

    private RunResultDao() {}

    /**
     * Inserts a new run result record into the database.
     * @param r RunResultRecord to insert
     * @throws SQLException if a database error occurs
     */
    public static void insert(RunResultRecord r) throws SQLException {
        String sql = """
            INSERT INTO run_results
              (run_id, completion_time_ticks, tasks_per_minute, avg_delivery_time_ticks,
               total_energy, energy_per_task, collisions, near_misses, deadlock_count,
               battery_deaths, fairness_gini, extra_metrics)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, r.getRunId());
            ps.setObject(2, r.getCompletionTimeTicks(), Types.INTEGER);
            ps.setBigDecimal(3, r.getTasksPerMinute());
            ps.setBigDecimal(4, r.getAvgDeliveryTimeTicks());
            ps.setBigDecimal(5, r.getTotalEnergy());
            ps.setBigDecimal(6, r.getEnergyPerTask());
            ps.setObject(7, r.getCollisions(), Types.INTEGER);
            ps.setObject(8, r.getNearMisses(), Types.INTEGER);
            ps.setObject(9, r.getDeadlockCount(), Types.INTEGER);
            ps.setObject(10, r.getBatteryDeaths(), Types.INTEGER);
            ps.setBigDecimal(11, r.getFairnessGini());
            ps.setObject(12, jsonb(r.getExtraMetrics()));
            ps.executeUpdate();
        }
    }

    /**
     * Finds a run result record by run ID.
     * @param runId ID of the run to find result for
     * @return Optional containing the run result record if found, otherwise empty
     * @throws SQLException if a database error occurs
     */
    public static Optional<RunResultRecord> findByRunId(UUID runId) throws SQLException {
        String sql = "SELECT * FROM run_results WHERE run_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Deletes a run result record by run ID.
     * @param runId ID of the run to delete result for
     * @return true if the run result was deleted, false otherwise
     * @throws SQLException if a database error occurs
     */
    public static boolean deleteByRunId(UUID runId) throws SQLException {
        String sql = "DELETE FROM run_results WHERE run_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Maps a ResultSet to a RunResultRecord.
     * @param rs ResultSet to map
     * @return RunResultRecord mapped from the ResultSet
     * @throws SQLException if a database error occurs
     */
    private static RunResultRecord map(ResultSet rs) throws SQLException {
        RunResultRecord r = new RunResultRecord();
        r.setRunId(rs.getObject("run_id", UUID.class));
        r.setCompletionTimeTicks((Integer) rs.getObject("completion_time_ticks"));
        r.setTasksPerMinute(rs.getBigDecimal("tasks_per_minute"));
        r.setAvgDeliveryTimeTicks(rs.getBigDecimal("avg_delivery_time_ticks"));
        r.setTotalEnergy(rs.getBigDecimal("total_energy"));
        r.setEnergyPerTask(rs.getBigDecimal("energy_per_task"));
        r.setCollisions((Integer) rs.getObject("collisions"));
        r.setNearMisses((Integer) rs.getObject("near_misses"));
        r.setDeadlockCount((Integer) rs.getObject("deadlock_count"));
        r.setBatteryDeaths((Integer) rs.getObject("battery_deaths"));
        r.setFairnessGini(rs.getBigDecimal("fairness_gini"));
        r.setExtraMetrics(rs.getString("extra_metrics"));
        return r;
    }

    /**
     * Converts a JSON string to a PGobject.
     * @param json JSON string to convert
     * @return PGobject containing the JSON
     * @throws SQLException if a database error occurs
     */
    private static PGobject jsonb(String json) throws SQLException {
        if (json == null) return null;
        PGobject obj = new PGobject();
        obj.setType("jsonb");
        obj.setValue(json);
        return obj;
    }
}
