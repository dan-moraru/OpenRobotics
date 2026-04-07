package com.openrobotics.db.model;

import java.math.BigDecimal;
import java.util.UUID;

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

    /**
     * Gets the ID of the robot run stats record.
     * @return ID of the robot run stats record
     */
    public Long getId() {
        return id; 
    }
    
    /**
     * Sets the ID of the robot run stats record.
     * @param id ID of the robot run stats record
     */
    public void setId(Long id) {
        this.id = id; 
    }


    /**
     * Gets the ID of the run of the robot run stats record.
     * @return ID of the run of the robot run stats record
     */
    public UUID getRunId() {
        return runId; 
    }
        
    /**
     * Sets the ID of the run of the robot run stats record.
     * @param runId ID of the run of the robot run stats record
     */
    public void setRunId(UUID runId) {
        this.runId = runId; 
    }


    /**
     * Gets the ID of the robot of the robot run stats record.
     * @return ID of the robot of the robot run stats record
     */
    public UUID getRobotId() {
        return robotId;
    }

    /**
     * Sets the ID of the robot of the robot run stats record.
     * @param robotId ID of the robot of the robot run stats record
     */
    public void setRobotId(UUID robotId) {
        this.robotId = robotId;
    }

    /**
     * Gets the navigation algorithm of the robot run stats record.
     * @return Navigation algorithm of the robot run stats record
     */
    public String getNavAlgorithm() {
        return navAlgorithm; 
    }

    /**
     * Sets the navigation algorithm of the robot run stats record.
     * @param navAlgorithm Navigation algorithm of the robot run stats record
     */
    public void setNavAlgorithm(String navAlgorithm) {
        this.navAlgorithm = navAlgorithm; 
    }

    /**
     * Gets the number of tasks completed by the robot.
     * @return Number of tasks completed by the robot
     */
    public Integer getTasksCompleted() {
        return tasksCompleted; 
    }

    /**
     * Sets the number of tasks completed by the robot.
     * @param tasksCompleted Number of tasks completed by the robot
     */
    public void setTasksCompleted(Integer tasksCompleted) { this.tasksCompleted = tasksCompleted; }

    /**
     * Gets the distance traveled by the robot.
     * @return Distance traveled by the robot
     */
    public BigDecimal getDistanceTraveled() {
        return distanceTraveled; 
    }

    /**
     * Sets the distance traveled by the robot.
     * @param distanceTraveled Distance traveled by the robot
     */
    public void setDistanceTraveled(BigDecimal distanceTraveled) {
        this.distanceTraveled = distanceTraveled; 
    }

    /**
     * Gets the energy used by the robot.
     * @return Energy used by the robot
     */
    public BigDecimal getEnergyUsed() {
        return energyUsed; 
    }

    /**
     * Sets the energy used by the robot.
     * @param energyUsed Energy used by the robot
     */
    public void setEnergyUsed(BigDecimal energyUsed) {
        this.energyUsed = energyUsed; 
    }

    /**
     * Gets the number of idle ticks by the robot.
     * @return Number of idle ticks by the robot
     */
        public Integer getIdleTicks() {
        return idleTicks; 
    }

    /**
     * Sets the number of idle ticks by the robot.
     * @param idleTicks Number of idle ticks by the robot
     */
    public void setIdleTicks(Integer idleTicks) {
        this.idleTicks = idleTicks; 
    }

    /**
     * Gets the number of wait ticks by the robot.
     * @return Number of wait ticks by the robot
     */
    public Integer getWaitTicks() {
        return waitTicks; 
    }

    /**
     * Sets the number of wait ticks by the robot.
     * @param waitTicks Number of wait ticks by the robot
     */
    public void setWaitTicks(Integer waitTicks) {
        this.waitTicks = waitTicks; 
    }

    /**
     * Gets the number of collisions by the robot.
     * @return Number of collisions by the robot
     */ 
    public Integer getCollisions() {
        return collisions; 
    }

    /**
     * Sets the number of collisions by the robot.
     * @param collisions Number of collisions by the robot
     */
    public void setCollisions(Integer collisions) {
        this.collisions = collisions; 
    }

    /**
     * Gets the number of near misses by the robot.
     * @return Number of near misses by the robot
     */
    public Integer getNearMisses() {
        return nearMisses;
    }

    /**
     * Sets the number of near misses by the robot.
     * @param nearMisses Number of near misses by the robot
    */
    public void setNearMisses(Integer nearMisses) {
        this.nearMisses = nearMisses; 
    }

    /**
     * Gets the number of deadlocks by the robot.
     * @return Number of deadlocks by the robot, may be null
     */
    public Integer getDeadlocks() {
        return deadlocks; 
    }

    /**
     * Sets the number of deadlocks by the robot.
     * @param deadlocks Number of deadlocks by the robot
     */
    public void setDeadlocks(Integer deadlocks) {
        this.deadlocks = deadlocks; 
    }
}
