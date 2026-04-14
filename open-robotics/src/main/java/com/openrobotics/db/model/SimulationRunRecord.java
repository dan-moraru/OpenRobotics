package com.openrobotics.db.model;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;

/** database record for the simulation_runs table */
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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getMapId() { return mapId; }
    public void setMapId(UUID mapId) { this.mapId = mapId; }

    public Integer getRobotCount() { return robotCount; }
    public void setRobotCount(Integer robotCount) {
        if (robotCount != null && robotCount < 0) {
            throw new IllegalArgumentException("robotCount must be non-negative");
        }
        this.robotCount = robotCount;
    }

    public String getCoordinationPolicy() { return coordinationPolicy; }
    public void setCoordinationPolicy(String coordinationPolicy) { this.coordinationPolicy = coordinationPolicy; }

    public String getRobotAlgorithms() { return robotAlgorithms; }
    public void setRobotAlgorithms(String robotAlgorithms) { this.robotAlgorithms = robotAlgorithms; }

    public Integer getWorkloadSeed() { return workloadSeed; }
    public void setWorkloadSeed(Integer workloadSeed) { this.workloadSeed = workloadSeed; }

    public String getWorkloadSettings() { return workloadSettings; }
    public void setWorkloadSettings(String workloadSettings) { this.workloadSettings = workloadSettings; }

    public String getSimSettings() { return simSettings; }
    public void setSimSettings(String simSettings) { this.simSettings = simSettings; }

    public Timestamp getStartedAt() { return startedAt == null ? null : new Timestamp(startedAt.getTime()); }
    public void setStartedAt(Timestamp startedAt) { this.startedAt = startedAt == null ? null : new Timestamp(startedAt.getTime()); }

    public Timestamp getFinishedAt() { return finishedAt == null ? null : new Timestamp(finishedAt.getTime()); }
    public void setFinishedAt(Timestamp finishedAt) { this.finishedAt = finishedAt == null ? null : new Timestamp(finishedAt.getTime()); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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
