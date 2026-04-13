package com.openrobotics.db.dao;

import com.openrobotics.db.Database;
import com.openrobotics.db.model.SimLogRecord;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** DAO for the sim_logs table */
public final class SimLogDao {

    private SimLogDao() {}

    /**
     * inserts a single log record and returns its generated ID.
     *
     * @throws SQLException on database error
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
            ps.setObject(3, r.getRobotId());
            ps.setString(4, r.getEventType());
            ps.setObject(5, r.getX(), Types.INTEGER);
            ps.setObject(6, r.getY(), Types.INTEGER);
            ps.setObject(7, jsonb(r.getDetails()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for insert into sim_logs");
                }
                return keys.getLong(1);
            }
        }
    }

    /**
     * inserts a batch of log records in a single transaction for better throughput.
     *
     * @throws SQLException on database error
     */
    public static void insertBatch(List<SimLogRecord> records) throws SQLException {
        if (records.isEmpty()) return;
        String sql = """
            INSERT INTO sim_logs (run_id, tick, robot_id, event_type, x, y, details)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            boolean originalAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                for (SimLogRecord r : records) {
                    ps.setObject(1, r.getRunId());
                    ps.setObject(2, r.getTick(), Types.INTEGER);
                    ps.setObject(3, r.getRobotId());
                    ps.setString(4, r.getEventType());
                    ps.setObject(5, r.getX(), Types.INTEGER);
                    ps.setObject(6, r.getY(), Types.INTEGER);
                    ps.setObject(7, jsonb(r.getDetails()));
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /**
     * finds all log records for a run, ordered by tick then ID.
     *
     * @throws SQLException on database error
     */
    public static List<SimLogRecord> findByRunId(UUID runId) throws SQLException {
        String sql = "SELECT * FROM sim_logs WHERE run_id = ? ORDER BY tick, id";
        return query(sql, runId);
    }

    /**
     * finds all log records for a run at a specific tick, ordered by ID.
     *
     * @throws SQLException on database error
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
     * deletes all log records for a run.
     *
     * @return number of deleted rows
     * @throws SQLException on database error
     */
    public static int deleteByRunId(UUID runId) throws SQLException {
        String sql = "DELETE FROM sim_logs WHERE run_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            return ps.executeUpdate();
        }
    }

    // executes a run_id-filtered query and collects results
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

    // maps a ResultSet row to a SimLogRecord
    private static SimLogRecord map(ResultSet rs) throws SQLException {
        SimLogRecord r = new SimLogRecord();
        r.setId(rs.getLong("id"));
        r.setRunId(rs.getObject("run_id", UUID.class));
        r.setTick((Integer) rs.getObject("tick"));
        r.setRobotId(rs.getObject("robot_id", UUID.class));
        r.setEventType(rs.getString("event_type"));
        r.setX((Integer) rs.getObject("x"));
        r.setY((Integer) rs.getObject("y"));
        r.setDetails(rs.getString("details"));
        return r;
    }

    // wraps a JSON string as a PostgreSQL jsonb value; returns null if json is null
    private static PGobject jsonb(String json) throws SQLException {
        if (json == null) return null;
        PGobject obj = new PGobject();
        obj.setType("jsonb");
        obj.setValue(json);
        return obj;
    }
}
