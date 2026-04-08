package com.openrobotics.io;

import java.util.List;

/**
 * Data transfer object for serializing post-simulation results to JSON.
 */
public class ResultsExportDTO {

    public String runName;
    public int totalTicks;
    public int totalTasks;
    public int completedTasks;
    public int pendingTasks;
    public double throughput;
    public double completionRate;
    public List<RobotResultDTO> robots;

    public static class RobotResultDTO {
        public String name;
        public String navigationAlgorithm;
        public int tasksCompleted;
        public int totalDistanceMoved;
        public double totalEnergyConsumed;
        public int totalIdleTicks;
        public int stuckTicks;
        public double battery;
        public String finalState;
    }
}