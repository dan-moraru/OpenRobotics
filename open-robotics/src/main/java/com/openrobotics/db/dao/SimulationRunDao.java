package com.openrobotics.db.dao;

import com.openrobotics.db.Database;
import com.openrobotics.db.model.SimulationRunRecord;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SimulationRunDao {

    private SimulationRunDao() {}

    /**
     * Inserts a new simulation run record into the database.
     * @param r SimulationRunRecord to insert
     * @return UUID of the inserted simulation run
     * @throws SQLException if a database error occurs
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
            ps.setInt(3, r.getRobotCount());
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
     * Finds a simulation run record by its ID.
     * @param id ID of the simulation run to find
     * @return Optional containing the simulation run record if found, otherwise empty
     * @throws SQLException if a database error occurs
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
     * Finds all simulation run records by map ID.
     * @param mapId ID of the map to find runs for
     * @return List of simulation run records
     * @throws SQLException if a database error occurs
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
     * Updates the status of a simulation run record.
     * @param id ID of the simulation run to update
     * @param status New status of the simulation run
     * @param finishedAt Timestamp of the finished time of the simulation run
     * @throws SQLException if a database error occurs
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
     * Deletes a simulation run record by its ID.
     * @param id ID of the simulation run to delete
     * @return true if the simulation run was deleted, false otherwise
     * @throws SQLException if a database error occurs
     */
    public static boolean deleteById(UUID id) throws SQLException {
        String sql = "DELETE FROM simulation_runs WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Maps a ResultSet to a SimulationRunRecord.
     * @param rs ResultSet to map
     * @return SimulationRunRecord mapped from the ResultSet
     * @throws SQLException if a database error occurs
     */
    private static SimulationRunRecord map(ResultSet rs) throws SQLException {
        SimulationRunRecord r = new SimulationRunRecord();
        r.setId(rs.getObject("id", UUID.class));
        r.setMapId(rs.getObject("map_id", UUID.class));
        r.setRobotCount(rs.getInt("robot_count"));
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
