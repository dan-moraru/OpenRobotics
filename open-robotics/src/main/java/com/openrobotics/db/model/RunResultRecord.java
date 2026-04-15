package com.openrobotics.db.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Database record for the {@code run_results} table. */
public class RunResultRecord {

    private UUID runId;
    private Integer completionTimeTicks;
    private BigDecimal tasksPerMinute;
    private BigDecimal avgDeliveryTimeTicks;
    private BigDecimal totalEnergy;
    private BigDecimal energyPerTask;
    private Integer collisions;
    private Integer nearMisses;
    private Integer deadlockCount;
    private Integer batteryDeaths;
    private BigDecimal fairnessGini;
    private String extraMetrics;

    public RunResultRecord() {}

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public Integer getCompletionTimeTicks() { return completionTimeTicks; }
    public void setCompletionTimeTicks(Integer completionTimeTicks) { this.completionTimeTicks = completionTimeTicks; }

    public BigDecimal getTasksPerMinute() { return tasksPerMinute; }
    public void setTasksPerMinute(BigDecimal tasksPerMinute) { this.tasksPerMinute = tasksPerMinute; }

    public BigDecimal getAvgDeliveryTimeTicks() { return avgDeliveryTimeTicks; }
    public void setAvgDeliveryTimeTicks(BigDecimal avgDeliveryTimeTicks) { this.avgDeliveryTimeTicks = avgDeliveryTimeTicks; }

    public BigDecimal getTotalEnergy() { return totalEnergy; }
    public void setTotalEnergy(BigDecimal totalEnergy) { this.totalEnergy = totalEnergy; }

    public BigDecimal getEnergyPerTask() { return energyPerTask; }
    public void setEnergyPerTask(BigDecimal energyPerTask) { this.energyPerTask = energyPerTask; }

    public Integer getCollisions() { return collisions; }
    public void setCollisions(Integer collisions) { this.collisions = collisions; }

    public Integer getNearMisses() { return nearMisses; }
    public void setNearMisses(Integer nearMisses) { this.nearMisses = nearMisses; }

    public Integer getDeadlockCount() { return deadlockCount; }
    public void setDeadlockCount(Integer deadlockCount) { this.deadlockCount = deadlockCount; }

    public Integer getBatteryDeaths() { return batteryDeaths; }
    public void setBatteryDeaths(Integer batteryDeaths) { this.batteryDeaths = batteryDeaths; }

    public BigDecimal getFairnessGini() { return fairnessGini; }
    public void setFairnessGini(BigDecimal fairnessGini) { this.fairnessGini = fairnessGini; }

    public String getExtraMetrics() { return extraMetrics; }
    public void setExtraMetrics(String extraMetrics) { this.extraMetrics = extraMetrics; }
}
