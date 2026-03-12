package com.openrobotics.db.dao;

import com.openrobotics.db.Database;
import com.openrobotics.db.model.SimLogRecord;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SimLogDao {

    private SimLogDao() {}

    /**
     * Inserts a new simulation log record into the database.
     * @param r SimLogRecord to insert
     * @return ID of the inserted simulation log
     * @throws SQLException if a database error occurs
     */
    public static long insert(SimLogRecord r) throws SQLException {
        String sql = """
            INSERT INTO sim_logs (run_id, tick, robot_id, event_type, x, y, details)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, r.getRunId());
            ps.setObject(2, r.getTick(), Types.INTEGER);
            ps.setObject(3, r.getRobotId(), Types.INTEGER);
            ps.setString(4, r.getEventType());
            ps.setObject(5, r.getX(), Types.INTEGER);
            ps.setObject(6, r.getY(), Types.INTEGER);
            ps.setObject(7, jsonb(r.getDetails()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /**
     * Inserts multiple log records in a single batch for better throughput during simulation ticks.
     * @param records List of SimLogRecord to insert
     * @throws SQLException if a database error occurs
     */
    public static void insertBatch(List<SimLogRecord> records) throws SQLException {
        if (records.isEmpty()) return;
        String sql = """
            INSERT INTO sim_logs (run_id, tick, robot_id, event_type, x, y, details)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (SimLogRecord r : records) {
                ps.setObject(1, r.getRunId());
                ps.setObject(2, r.getTick(), Types.INTEGER);
                ps.setObject(3, r.getRobotId(), Types.INTEGER);
                ps.setString(4, r.getEventType());
                ps.setObject(5, r.getX(), Types.INTEGER);
                ps.setObject(6, r.getY(), Types.INTEGER);
                ps.setObject(7, jsonb(r.getDetails()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Finds all simulation log records by run ID.
     * @param runId ID of the run to find logs for
     * @return List of simulation log records
     * @throws SQLException if a database error occurs
     */
    public static List<SimLogRecord> findByRunId(UUID runId) throws SQLException {
        String sql = "SELECT * FROM sim_logs WHERE run_id = ? ORDER BY tick, id";
        return query(sql, runId);
    }

    /**
     * Finds all simulation log records by run ID and tick.
     * @param runId ID of the run to find logs for
     * @param tick Tick to find logs for
     * @return List of simulation log records
     * @throws SQLException if a database error occurs
     */
    public static List<SimLogRecord> findByRunIdAndTick(UUID runId, int tick) throws SQLException {
        String sql = "SELECT * FROM sim_logs WHERE run_id = ? AND tick = ? ORDER BY id";
        List<SimLogRecord> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setInt(2, tick);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /**
     * Deletes all simulation log records by run ID.
     * @param runId ID of the run to delete logs for
     * @return Number of deleted records
     * @throws SQLException if a database error occurs
     */
    public static int deleteByRunId(UUID runId) throws SQLException {
        String sql = "DELETE FROM sim_logs WHERE run_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            return ps.executeUpdate();
        }
    }

    /**
     * Executes a query and maps the results to a list of SimLogRecord.
     * @param sql SQL query to execute
     * @param runId ID of the run to find logs for
     * @return List of simulation log records
     * @throws SQLException if a database error occurs
     */
    private static List<SimLogRecord> query(String sql, UUID runId) throws SQLException {
        List<SimLogRecord> list = new ArrayList<>();
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
     * Maps a ResultSet to a SimLogRecord.
     * @param rs ResultSet to map
     * @return SimLogRecord mapped from the ResultSet
     * @throws SQLException if a database error occurs
     */
    private static SimLogRecord map(ResultSet rs) throws SQLException {
        SimLogRecord r = new SimLogRecord();
        r.setId(rs.getLong("id"));
        r.setRunId(rs.getObject("run_id", UUID.class));
        r.setTick((Integer) rs.getObject("tick"));
        r.setRobotId((Integer) rs.getObject("robot_id"));
        r.setEventType(rs.getString("event_type"));
        r.setX((Integer) rs.getObject("x"));
        r.setY((Integer) rs.getObject("y"));
        r.setDetails(rs.getString("details"));
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
