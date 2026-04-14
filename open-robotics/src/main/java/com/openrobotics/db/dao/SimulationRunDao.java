package com.openrobotics.db.dao;

import com.openrobotics.db.Database;
import com.openrobotics.db.model.SimulationRunRecord;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** DAO for the simulation_runs table */
public final class SimulationRunDao {

    private SimulationRunDao() {}

    /**
     * inserts a simulation run record; uses {@code r.getId()} if set, otherwise generates a new UUID.
     *
     * @return the inserted run's ID
     * @throws SQLException on database error
     */
    public static UUID insert(SimulationRunRecord r) throws SQLException {
        UUID id = r.getId() != null ? r.getId() : UUID.randomUUID();
        String sql = """
            INSERT INTO simulation_runs
              (id, map_id, robot_count, coordination_policy, robot_algorithms,
               workload_seed, workload_settings, sim_settings, started_at, finished_at, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, r.getMapId());
            ps.setObject(3, r.getRobotCount(), Types.INTEGER);
            ps.setString(4, r.getCoordinationPolicy());
            ps.setObject(5, jsonb(r.getRobotAlgorithms()));
            ps.setObject(6, r.getWorkloadSeed(), Types.INTEGER);
            ps.setObject(7, jsonb(r.getWorkloadSettings()));
            ps.setObject(8, jsonb(r.getSimSettings()));
            ps.setTimestamp(9, r.getStartedAt());
            ps.setTimestamp(10, r.getFinishedAt());
            ps.setString(11, r.getStatus());
            ps.executeUpdate();
        }
        return id;
    }

    /**
     * finds a simulation run record by ID.
     *
     * @throws SQLException on database error
     */
    public static Optional<SimulationRunRecord> findById(UUID id) throws SQLException {
        String sql = "SELECT * FROM simulation_runs WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * finds all simulation run records for a given map, ordered by started_at descending.
     *
     * @throws SQLException on database error
     */
    public static List<SimulationRunRecord> findByMapId(UUID mapId) throws SQLException {
        String sql = "SELECT * FROM simulation_runs WHERE map_id = ? ORDER BY started_at DESC";
        List<SimulationRunRecord> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, mapId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    /**
     * updates the status and finished_at timestamp of a simulation run.
     *
     * @throws SQLException on database error
     */
    public static void updateStatus(UUID id, String status, Timestamp finishedAt) throws SQLException {
        String sql = "UPDATE simulation_runs SET status = ?, finished_at = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setTimestamp(2, finishedAt);
            ps.setObject(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * deletes a simulation run record by ID.
     *
     * @return true if a row was deleted
     * @throws SQLException on database error
     */
    public static boolean deleteById(UUID id) throws SQLException {
        String sql = "DELETE FROM simulation_runs WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // maps a ResultSet row to a SimulationRunRecord
    private static SimulationRunRecord map(ResultSet rs) throws SQLException {
        SimulationRunRecord r = new SimulationRunRecord();
        r.setId(rs.getObject("id", UUID.class));
        r.setMapId(rs.getObject("map_id", UUID.class));
        r.setRobotCount(rs.getObject("robot_count", Integer.class));
        r.setCoordinationPolicy(rs.getString("coordination_policy"));
        r.setRobotAlgorithms(rs.getString("robot_algorithms"));
        r.setWorkloadSeed((Integer) rs.getObject("workload_seed"));
        r.setWorkloadSettings(rs.getString("workload_settings"));
        r.setSimSettings(rs.getString("sim_settings"));
        r.setStartedAt(rs.getTimestamp("started_at"));
        r.setFinishedAt(rs.getTimestamp("finished_at"));
        r.setStatus(rs.getString("status"));
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
