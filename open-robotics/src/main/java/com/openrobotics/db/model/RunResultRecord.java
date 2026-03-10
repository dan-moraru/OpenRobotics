package com.openrobotics.db.model;

import java.math.BigDecimal;
import java.util.UUID;

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

    /**
     * Gets the ID of the run of the run result record.
     * @return ID of the run of the run result record
     */ 
    public UUID getRunId() {
        return runId; 
    }

    /**
     * Sets the ID of the run of the run result record.
     * @param runId ID of the run of the run result record
     */
    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    /**
     * Gets the completion time ticks of the run result record.
     * @return Completion time ticks of the run result record
     */
    public Integer getCompletionTimeTicks() {
        return completionTimeTicks; 
    }

    /**
     * Sets the completion time ticks of the run result record.
     * @param completionTimeTicks Completion time ticks of the run result record
     */
    public void setCompletionTimeTicks(Integer completionTimeTicks) {
        this.completionTimeTicks = completionTimeTicks;
    }

    /**
     * Gets the tasks per minute of the run result record.
     * @return Tasks per minute of the run result record
     */
    public BigDecimal getTasksPerMinute() {
        return tasksPerMinute; 
    }

    /**
     * Sets the tasks per minute of the run result record.
     * @param tasksPerMinute Tasks per minute of the run result record
     */
    public void setTasksPerMinute(BigDecimal tasksPerMinute) {
        this.tasksPerMinute = tasksPerMinute;
    }

    /**
     * Gets the average delivery time ticks of the run result record.
     * @return Average delivery time ticks of the run result record
     */
    public BigDecimal getAvgDeliveryTimeTicks() {
        return avgDeliveryTimeTicks; 
    }

    /**
     * Sets the average delivery time ticks of the run result record.
     * @param avgDeliveryTimeTicks Average delivery time ticks of the run result record
     */
    public void setAvgDeliveryTimeTicks(BigDecimal avgDeliveryTimeTicks) {
        this.avgDeliveryTimeTicks = avgDeliveryTimeTicks;
    }

    /**
     * Gets the total energy of the run result record.
     * @return Total energy of the run result record
     */
    public BigDecimal getTotalEnergy() {
        return totalEnergy; 
    }

    /**
     * Sets the total energy of the run result record.
     * @param totalEnergy Total energy of the run result record
     */
    public void setTotalEnergy(BigDecimal totalEnergy) { this.totalEnergy = totalEnergy; }

    /**
     * Gets the energy per task of the run result record.
     * @return Energy per task of the run result record
     */
    public BigDecimal getEnergyPerTask() {
        return energyPerTask; 
    }

    /**
     * Sets the energy per task of the run result record.
     * @param energyPerTask Energy per task of the run result record
     */
    public void setEnergyPerTask(BigDecimal energyPerTask) {
        this.energyPerTask = energyPerTask;
    }

    /**
     * Gets the collisions of the run result record.
     * @return Collisions of the run result record
     */
    public Integer getCollisions() {
        return collisions; 
    }

    /**
     * Sets the collisions of the run result record.
     * @param collisions Collisions of the run result record
     */
    public void setCollisions(Integer collisions) {
        this.collisions = collisions;
    }

    /**
     * Gets the near misses of the run result record.
     * @return Near misses of the run result record
     */
    public Integer getNearMisses() {
        return nearMisses; 
    }

    /**
     * Sets the near misses of the run result record.
     * @param nearMisses Near misses of the run result record
     */
    public void setNearMisses(Integer nearMisses) {
        this.nearMisses = nearMisses;
    }

    /**
     * Gets the deadlock count of the run result record.
     * @return Deadlock count of the run result record
     */
    public Integer getDeadlockCount() {
        return deadlockCount; 
    }

    /**
     * Sets the deadlock count of the run result record.
     * @param deadlockCount Deadlock count of the run result record
     */
    public void setDeadlockCount(Integer deadlockCount) {
        this.deadlockCount = deadlockCount;
    }

    /**
     * Gets the battery deaths of the run result record.
     * @return Battery deaths of the run result record
     */
    public Integer getBatteryDeaths() {
        return batteryDeaths; 
    }

    /**
     * Sets the battery deaths of the run result record.
     * @param batteryDeaths Battery deaths of the run result record
     */
    public void setBatteryDeaths(Integer batteryDeaths) {
        this.batteryDeaths = batteryDeaths;
    }

    /**
     * Gets the fairness Gini of the run result record.
     * @return Fairness Gini of the run result record
     */
    public BigDecimal getFairnessGini() {
        return fairnessGini; 
    }

    /**
     * Sets the fairness Gini of the run result record.
     * @param fairnessGini Fairness Gini of the run result record
     */
    public void setFairnessGini(BigDecimal fairnessGini) {
        this.fairnessGini = fairnessGini;
    }

    /**
     * Gets the extra metrics of the run result record.
     * @return Extra metrics of the run result record
     */
    public String getExtraMetrics() {
        return extraMetrics; 
    }

    /**
     * Sets the extra metrics of the run result record.
     * @param extraMetrics Extra metrics of the run result record
     */
    public void setExtraMetrics(String extraMetrics) {
        this.extraMetrics = extraMetrics;
    }
}
