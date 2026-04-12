package com.openrobotics.db.dao;

import com.openrobotics.db.Database;
import com.openrobotics.db.model.RobotRunStatsRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RobotRunStatsDao {

    private RobotRunStatsDao() {}

    /**
     * Upserts robot stats: inserts a new row or updates the existing one for (run_id, robot_id).
     * @param r RobotRunStatsRecord to upsert
     * @return ID of the upserted robot stats
     * @throws SQLException if a database error occurs
     */
    public static long upsert(RobotRunStatsRecord r) throws SQLException {
        String sql = """
            INSERT INTO robot_run_stats
              (run_id, robot_id, nav_algorithm, tasks_completed, distance_traveled,
               energy_used, idle_ticks, wait_ticks, collisions, near_misses, deadlocks)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (run_id, robot_id) DO UPDATE SET
              nav_algorithm     = EXCLUDED.nav_algorithm,
              tasks_completed   = EXCLUDED.tasks_completed,
              distance_traveled = EXCLUDED.distance_traveled,
              energy_used       = EXCLUDED.energy_used,
              idle_ticks        = EXCLUDED.idle_ticks,
              wait_ticks        = EXCLUDED.wait_ticks,
              collisions        = EXCLUDED.collisions,
              near_misses       = EXCLUDED.near_misses,
              deadlocks         = EXCLUDED.deadlocks
            RETURNING id
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, r.getRunId());
            ps.setObject(2, r.getRobotId());
            ps.setString(3, r.getNavAlgorithm());
            ps.setObject(4, r.getTasksCompleted(), Types.INTEGER);
            ps.setBigDecimal(5, r.getDistanceTraveled());
            ps.setBigDecimal(6, r.getEnergyUsed());
            ps.setObject(7, r.getIdleTicks(), Types.INTEGER);
            ps.setObject(8, r.getWaitTicks(), Types.INTEGER);
            ps.setObject(9, r.getCollisions(), Types.INTEGER);
            ps.setObject(10, r.getNearMisses(), Types.INTEGER);
            ps.setObject(11, r.getDeadlocks(), Types.INTEGER);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Expected upsert to return a row but none was returned");
                }
                return rs.getLong(1);
            }
        }
    }

    /**
     * Finds all robot run stats records by run ID.
     * @param runId ID of the run to find stats for
     * @return List of robot run stats records
     * @throws SQLException if a database error occurs
     */
    public static List<RobotRunStatsRecord> findByRunId(UUID runId) throws SQLException {
        String sql = "SELECT * FROM robot_run_stats WHERE run_id = ? ORDER BY robot_id";
        List<RobotRunStatsRecord> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /**
     * Finds a robot run stats record by run ID and robot ID.
     * @param runId ID of the run to find stats for
     * @param robotId ID of the robot to find stats for
     * @return Optional containing the robot run stats record if found, otherwise empty
     * @throws SQLException if a database error occurs
     */
    public static Optional<RobotRunStatsRecord> findByRunIdAndRobotId(UUID runId, UUID robotId) throws SQLException {
        String sql = "SELECT * FROM robot_run_stats WHERE run_id = ? AND robot_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setObject(2, robotId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Deletes all robot run stats records by run ID.
     * @param runId ID of the run to delete stats for
     * @return Number of deleted records
     * @throws SQLException if a database error occurs
     */
    public static int deleteByRunId(UUID runId) throws SQLException {
        String sql = "DELETE FROM robot_run_stats WHERE run_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            return ps.executeUpdate();
        }
    }

    /**
     * Maps a ResultSet to a RobotRunStatsRecord.
     * @param rs ResultSet to map
     * @return RobotRunStatsRecord mapped from the ResultSet
     * @throws SQLException if a database error occurs
     */
    private static RobotRunStatsRecord map(ResultSet rs) throws SQLException {
        RobotRunStatsRecord r = new RobotRunStatsRecord();
        r.setId(rs.getLong("id"));
        r.setRunId(rs.getObject("run_id", UUID.class));
        r.setRobotId(rs.getObject("robot_id", UUID.class));
        r.setNavAlgorithm(rs.getString("nav_algorithm"));
        r.setTasksCompleted((Integer) rs.getObject("tasks_completed"));
        r.setDistanceTraveled(rs.getBigDecimal("distance_traveled"));
        r.setEnergyUsed(rs.getBigDecimal("energy_used"));
        r.setIdleTicks((Integer) rs.getObject("idle_ticks"));
        r.setWaitTicks((Integer) rs.getObject("wait_ticks"));
        r.setCollisions((Integer) rs.getObject("collisions"));
        r.setNearMisses((Integer) rs.getObject("near_misses"));
        r.setDeadlocks((Integer) rs.getObject("deadlocks"));
        return r;
    }
}
