package com.openrobotics.db.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Database record for the {@code robot_run_stats} table. */
public class RobotRunStatsRecord {

    private Long id;
    private UUID runId;
    private UUID robotId;
    private String navAlgorithm;
    private Integer tasksCompleted;
    private BigDecimal distanceTraveled;
    private BigDecimal energyUsed;
    private Integer idleTicks;
    private Integer waitTicks;
    private Integer collisions;
    private Integer nearMisses;
    private Integer deadlocks;

    public RobotRunStatsRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public UUID getRobotId() { return robotId; }
    public void setRobotId(UUID robotId) { this.robotId = robotId; }

    public String getNavAlgorithm() { return navAlgorithm; }
    public void setNavAlgorithm(String navAlgorithm) { this.navAlgorithm = navAlgorithm; }

    public Integer getTasksCompleted() { return tasksCompleted; }
    public void setTasksCompleted(Integer tasksCompleted) { this.tasksCompleted = tasksCompleted; }

    public BigDecimal getDistanceTraveled() { return distanceTraveled; }
    public void setDistanceTraveled(BigDecimal distanceTraveled) { this.distanceTraveled = distanceTraveled; }

    public BigDecimal getEnergyUsed() { return energyUsed; }
    public void setEnergyUsed(BigDecimal energyUsed) { this.energyUsed = energyUsed; }

    public Integer getIdleTicks() { return idleTicks; }
    public void setIdleTicks(Integer idleTicks) { this.idleTicks = idleTicks; }

    public Integer getWaitTicks() { return waitTicks; }
    public void setWaitTicks(Integer waitTicks) { this.waitTicks = waitTicks; }

    public Integer getCollisions() { return collisions; }
    public void setCollisions(Integer collisions) { this.collisions = collisions; }

    public Integer getNearMisses() { return nearMisses; }
    public void setNearMisses(Integer nearMisses) { this.nearMisses = nearMisses; }

    public Integer getDeadlocks() { return deadlocks; }
    public void setDeadlocks(Integer deadlocks) { this.deadlocks = deadlocks; }
}
