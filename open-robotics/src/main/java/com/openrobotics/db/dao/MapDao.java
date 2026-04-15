package com.openrobotics.db.dao;

import com.openrobotics.db.Database;
import com.openrobotics.db.model.MapRecord;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** DAO for the {@code maps} table. */
public final class MapDao {

    private MapDao() {}

    /**
     * Inserts a map record; uses {@code r.getId()} if set, otherwise generates a new UUID.
     *
     * @param r the map record to insert
     * @return the inserted map's ID
     * @throws SQLException on database error
     */
    public static UUID insert(MapRecord r) throws SQLException {
        UUID id = r.getId() != null ? r.getId() : UUID.randomUUID();
        String sql = """
            INSERT INTO maps (id, name, width, height, tile_data, is_preset, random_seed, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, now()))
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, r.getName());
            ps.setInt(3, r.getWidth());
            ps.setInt(4, r.getHeight());
            ps.setObject(5, jsonb(r.getTileData()));
            ps.setBoolean(6, r.isPreset());
            ps.setObject(7, r.getRandomSeed(), Types.INTEGER);
            ps.setTimestamp(8, r.getCreatedAt());
            ps.executeUpdate();
        }
        return id;
    }

    /**
     * Finds a map record by ID.
     *
     * @param id the map UUID to look up
     * @return an {@link Optional} containing the record, or empty if not found
     * @throws SQLException on database error
     */
    public static Optional<MapRecord> findById(UUID id) throws SQLException {
        String sql = "SELECT * FROM maps WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Returns all map records ordered by {@code created_at} descending.
     *
     * @return list of all map records
     * @throws SQLException on database error
     */
    public static List<MapRecord> findAll() throws SQLException {
        String sql = "SELECT * FROM maps ORDER BY created_at DESC";
        List<MapRecord> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Deletes a map record by ID.
     *
     * @param id the UUID of the map record to delete
     * @return {@code true} if a row was deleted
     * @throws SQLException on database error
     */
    public static boolean deleteById(UUID id) throws SQLException {
        String sql = "DELETE FROM maps WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // maps a ResultSet row to a MapRecord
    private static MapRecord map(ResultSet rs) throws SQLException {
        MapRecord r = new MapRecord();
        r.setId(rs.getObject("id", UUID.class));
        r.setName(rs.getString("name"));
        r.setWidth(rs.getInt("width"));
        r.setHeight(rs.getInt("height"));
        r.setTileData(rs.getString("tile_data"));
        r.setPreset(rs.getBoolean("is_preset"));
        r.setRandomSeed((Integer) rs.getObject("random_seed"));
        r.setCreatedAt(rs.getTimestamp("created_at"));
        return r;
    }

    // wraps a JSON string as a PostgreSQL jsonb value
    private static PGobject jsonb(String json) throws SQLException {
        PGobject obj = new PGobject();
        obj.setType("jsonb");
        obj.setValue(json);
        return obj;
    }
}
