package com.openrobotics.db.dao;

import com.openrobotics.db.Database;
import com.openrobotics.db.model.WorkloadTaskRecord;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** DAO for the {@code run_workload_tasks} table. */
public final class WorkloadTaskDao {

    private WorkloadTaskDao() {}

    /**
     * Inserts a workload task record and returns its generated ID.
     *
     * @param r the workload task record to insert
     * @return the generated row ID
     * @throws SQLException on database error
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
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for insert into run_workload_tasks");
                }
                return keys.getLong(1);
            }
        }
    }

    /**
     * Finds all workload task records for a given run, ordered by ID.
     *
     * @param runId the run UUID to query
     * @return list of workload task records for the run
     * @throws SQLException on database error
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
     * Updates the {@code status} and {@code completed_tick} for a task.
     *
     * @param id the row ID of the task to update
     * @param status the new status string
     * @param completedTick the tick at which the task was completed; may be {@code null}
     * @throws SQLException on database error
     */
    public static void markCompleted(long id, String status, Integer completedTick) throws SQLException {
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
     * Assigns a task to a robot and sets its status to {@code IN_PROGRESS}.
     *
     * @param id the row ID of the task to assign
     * @param robotId the UUID of the robot being assigned
     * @param assignedTick the tick at which the assignment occurs
     * @throws SQLException on database error
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
     * Deletes all workload task records for a given run.
     *
     * @param runId the run UUID whose workload task records should be deleted
     * @return the number of deleted rows
     * @throws SQLException on database error
     */
    public static int deleteByRunId(UUID runId) throws SQLException {
        String sql = "DELETE FROM run_workload_tasks WHERE run_id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            return ps.executeUpdate();
        }
    }

    // maps a ResultSet row to a WorkloadTaskRecord
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

    // wraps a JSON string as a PostgreSQL jsonb value; returns null if json is null
    private static PGobject jsonb(String json) throws SQLException {
        if (json == null) return null;
        PGobject obj = new PGobject();
        obj.setType("jsonb");
        obj.setValue(json);
        return obj;
    }
}
