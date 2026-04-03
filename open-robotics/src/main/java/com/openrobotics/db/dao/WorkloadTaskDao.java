package com.openrobotics.db.dao;

import com.openrobotics.db.Database;
import com.openrobotics.db.model.WorkloadTaskRecord;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WorkloadTaskDao {

    private WorkloadTaskDao() {}

    /**
     * Inserts a new workload task record into the database.
     * @param r WorkloadTaskRecord to insert
     * @return ID of the inserted workload task
     * @throws SQLException if a database error occurs
     */
    public static long insert(WorkloadTaskRecord r) throws SQLException {
        String sql = """
            INSERT INTO run_workload_tasks
              (run_id, task_type, priority, created_tick, assigned_tick, completed_tick,
               pickup_x, pickup_y, dropoff_x, dropoff_y, status, assigned_robot_id, details)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, r.getRunId());
            ps.setString(2, r.getTaskType());
            ps.setObject(3, r.getPriority(), Types.INTEGER);
            ps.setObject(4, r.getCreatedTick(), Types.INTEGER);
            ps.setObject(5, r.getAssignedTick(), Types.INTEGER);
            ps.setObject(6, r.getCompletedTick(), Types.INTEGER);
            ps.setObject(7, r.getPickupX(), Types.INTEGER);
            ps.setObject(8, r.getPickupY(), Types.INTEGER);
            ps.setObject(9, r.getDropoffX(), Types.INTEGER);
            ps.setObject(10, r.getDropoffY(), Types.INTEGER);
            ps.setString(11, r.getStatus());
            ps.setObject(12, r.getAssignedRobotId());
            ps.setObject(13, jsonb(r.getDetails()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /**
     * Finds all workload task records by run ID.
     * @param runId ID of the run to find tasks for
     * @return List of workload task records
     * @throws SQLException if a database error occurs
     */
    public static List<WorkloadTaskRecord> findByRunId(UUID runId) throws SQLException {
        String sql = "SELECT * FROM run_workload_tasks WHERE run_id = ? ORDER BY id";
        List<WorkloadTaskRecord> list = new ArrayList<>();
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
     * Updates the status of a workload task record.
     * @param id ID of the task to update
     * @param status New status of the task
     * @param completedTick Tick when the task was completed
     * @throws SQLException if a database error occurs
     */
    public static void updateStatus(long id, String status, Integer completedTick) throws SQLException {
        String sql = "UPDATE run_workload_tasks SET status = ?, completed_tick = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setObject(2, completedTick, Types.INTEGER);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * Assigns a workload task to a robot.
     * @param id ID of the task to assign
     * @param robotId ID of the robot to assign the task to
     * @param assignedTick Tick when the task was assigned to the robot
     * @throws SQLException if a database error occurs
     */
    public static void assignToRobot(long id, UUID robotId, int assignedTick) throws SQLException {
        String sql = "UPDATE run_workload_tasks SET assigned_robot_id = ?, assigned_tick = ?, status = 'IN_PROGRESS' WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, robotId);
            ps.setInt(2, assignedTick);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * Deletes all workload task records by run ID.
     * @param runId ID of the run to delete tasks for
     * @return Number of deleted records
     * @throws SQLException if a database error occurs
     */
    public static int deleteByRunId(UUID runId) throws SQLException {
        String sql = "DELETE FROM run_workload_tasks WHERE run_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            return ps.executeUpdate();
        }
    }

    /**
     * Maps a ResultSet to a WorkloadTaskRecord.
     * @param rs ResultSet to map
     * @return WorkloadTaskRecord mapped from the ResultSet
     * @throws SQLException if a database error occurs
     */
    private static WorkloadTaskRecord map(ResultSet rs) throws SQLException {
        WorkloadTaskRecord r = new WorkloadTaskRecord();
        r.setId(rs.getLong("id"));
        r.setRunId(rs.getObject("run_id", UUID.class));
        r.setTaskType(rs.getString("task_type"));
        r.setPriority((Integer) rs.getObject("priority"));
        r.setCreatedTick((Integer) rs.getObject("created_tick"));
        r.setAssignedTick((Integer) rs.getObject("assigned_tick"));
        r.setCompletedTick((Integer) rs.getObject("completed_tick"));
        r.setPickupX((Integer) rs.getObject("pickup_x"));
        r.setPickupY((Integer) rs.getObject("pickup_y"));
        r.setDropoffX((Integer) rs.getObject("dropoff_x"));
        r.setDropoffY((Integer) rs.getObject("dropoff_y"));
        r.setStatus(rs.getString("status"));
        r.setAssignedRobotId(rs.getObject("assigned_robot_id", UUID.class));
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
