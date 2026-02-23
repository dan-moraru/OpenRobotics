package com.openrobotics.simulationcore;

public class SimulationEngine {
    private int tickCounter;
    private boolean running; // tracks if the simulation is still running
    private final CollisionManager collisionManager;

    public SimulationEngine() {
        tickCounter = 0;
        running = false;
        collisionManager = new CollisionManager();
    }

    // Runs a tick of the simulation
    public void tick() {
        // TODO
    }

    private void incrementTickCounter() {
        tickCounter++;
    }

    // Collects move intentions for all robots in the simulation
    private MoveIntention[] collectIntentions() {
        // TODO

        return null;
    }

    // Commits move intentions for all robots
    private void CommitMoveIntentions() {
        // TODO
    }

    // Updates states for all robots based on commited move intentions
    private void updateRobotStates() {
        // TODO
    }


}
