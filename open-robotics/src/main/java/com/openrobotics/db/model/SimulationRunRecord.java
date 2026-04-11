package com.openrobotics.db.model;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;

public class SimulationRunRecord {

    private UUID id;
    private UUID mapId;
    private Integer robotCount;
    private String coordinationPolicy;
    private String robotAlgorithms;
    private Integer workloadSeed;
    private String workloadSettings;
    private String simSettings;
    private Timestamp startedAt;
    private Timestamp finishedAt;
    private String status;

    public SimulationRunRecord() {}

    /**
     * Gets the ID of the simulation run record.
     * @return ID of the simulation run record
     */ 
    public UUID getId() {
        return id; 
    }

    /**
     * Sets the ID of the simulation run record.
     * @param id ID of the simulation run record
     */
    public void setId(UUID id) { 
        this.id = id; 
    }

    /**
     * Gets the ID of the map of the simulation run record.
     * @return ID of the map of the simulation run record
     */
    public UUID getMapId() {
        return mapId; 
    }

    /**
     * Sets the ID of the map of the simulation run record.
     * @param mapId ID of the map of the simulation run record
     */
    public void setMapId(UUID mapId) {
        this.mapId = mapId; 
    }

    /**
     * Gets the number of robots of the simulation run record.
     * @return Number of robots of the simulation run record
     */
    public Integer getRobotCount() {
        return robotCount; 
    }

    /**
     * Sets the number of robots of the simulation run record.
     * @param robotCount Number of robots of the simulation run record
     */
    public void setRobotCount(Integer robotCount) {
        if (robotCount != null && robotCount < 0) {
            throw new IllegalArgumentException("robotCount must be non-negative");
        }
        this.robotCount = robotCount; 
    }

    /**
     * Gets the coordination policy of the simulation run record.
     * @return Coordination policy of the simulation run record
     */
    public String getCoordinationPolicy() {
        return coordinationPolicy; 
    }

    /**
     * Sets the coordination policy of the simulation run record.
     * @param coordinationPolicy Coordination policy of the simulation run record
     */
    public void setCoordinationPolicy(String coordinationPolicy) {
        this.coordinationPolicy = coordinationPolicy; 
    }

    /**
     * Gets the robot algorithms of the simulation run record.
     * @return Robot algorithms of the simulation run record
     */
    public String getRobotAlgorithms() {
        return robotAlgorithms; 
    }

    /**
     * Sets the robot algorithms of the simulation run record.
     * @param robotAlgorithms Robot algorithms of the simulation run record
     */
    public void setRobotAlgorithms(String robotAlgorithms) {
        this.robotAlgorithms = robotAlgorithms; 
    }

    /**
     * Gets the workload seed of the simulation run record.
     * @return Workload seed of the simulation run record
     */
    public Integer getWorkloadSeed() {
        return workloadSeed; 
    }

    /**
     * Sets the workload seed of the simulation run record.
     * @param workloadSeed Workload seed of the simulation run record
     */
    public void setWorkloadSeed(Integer workloadSeed) {
        this.workloadSeed = workloadSeed; 
    }

    /**
     * Gets the workload settings of the simulation run record.
     * @return Workload settings of the simulation run record
     */
    public String getWorkloadSettings() {
        return workloadSettings; 
    }

    /**
     * Sets the workload settings of the simulation run record.
     * @param workloadSettings Workload settings of the simulation run record
     */
    public void setWorkloadSettings(String workloadSettings) {
        this.workloadSettings = workloadSettings; 
    }

    /**
     * Gets the simulation settings of the simulation run record.
     * @return Simulation settings of the simulation run record
     */ 
    public String getSimSettings() {
        return simSettings; 
    }

    /**
     * Sets the simulation settings of the simulation run record.
     * @param simSettings Simulation settings of the simulation run record
     */
    public void setSimSettings(String simSettings) {
        this.simSettings = simSettings; 
    }

    /**
     * Gets the started at timestamp of the simulation run record.
     * @return Started at timestamp of the simulation run record
     */
    public Timestamp getStartedAt() { 
        return startedAt == null ? null : new Timestamp(startedAt.getTime()); 
    }

    /**
     * Sets the started at timestamp of the simulation run record.
     * @param startedAt Started at timestamp of the simulation run record
     */
    public void setStartedAt(Timestamp startedAt) { 
        this.startedAt = startedAt == null ? null : new Timestamp(startedAt.getTime()); 
    }

    /**
     * Gets the finished at timestamp of the simulation run record.
     * @return Finished at timestamp of the simulation run record
     */
    public Timestamp getFinishedAt() {
        return finishedAt == null ? null : new Timestamp(finishedAt.getTime()); 
    }

    /**
     * Sets the finished at timestamp of the simulation run record.
     * @param finishedAt Finished at timestamp of the simulation run record
     */
    public void setFinishedAt(Timestamp finishedAt) {
        this.finishedAt = finishedAt == null ? null : new Timestamp(finishedAt.getTime()); 
    }

    /**
     * Gets the status of the simulation run record.
     * @return Status of the simulation run record
     */
    public String getStatus() {
        return status; 
    }

    /**
     * Sets the status of the simulation run record.
     * @param status Status of the simulation run record
     */
    public void setStatus(String status) {
        this.status = status; 
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimulationRunRecord other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SimulationRunRecord{" +
            "id=" + id +
            ", mapId=" + mapId +
            ", robotCount=" + robotCount +
            ", coordinationPolicy='" + coordinationPolicy + '\'' +
            ", robotAlgorithms='" + robotAlgorithms + '\'' +
            ", workloadSeed=" + workloadSeed +
            ", workloadSettings='" + workloadSettings + '\'' +
            ", simSettings='" + simSettings + '\'' +
            ", startedAt=" + startedAt +
            ", finishedAt=" + finishedAt +
            ", status='" + status + '\'' +
            '}';
    }
}
