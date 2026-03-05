package com.openrobotics.simulationcore;

import com.openrobotics.Map;
import com.openrobotics.Robot;
import com.openrobotics.Vector2D;

/**
 * The SimulationEngine is the core component responsible for advancing the
 * simulation. It coordinates the progression of discrete simulation steps
 * ("ticks") and manages interactions between robots within the environment.
 * The engine maintains an internal tick counter that represents the number
 * of simulation steps that have been executed.
 */
public class SimulationEngine {
    private int tickCounter;
    private Map map;
    private Robot[] robots;
    private final CollisionManager collisionManager;
    private Dispatcher dispatcher;
    private CoordinationPolicy coordinationPolicy;

    /**
     * Constructs a new simulation engine instance
     * @param map the map representing the warehouse environment
     * @param robots the robots
     * @param dispatcher the task dispatcher loaded with tasks ready to be dispatched to robots
     * @param coordinationPolicy the set coordination policy between robots that is followed when moving around the map
     */
    public SimulationEngine(Map map, Robot[] robots, Dispatcher dispatcher, CoordinationPolicy coordinationPolicy) {
        this.tickCounter = 0;
        this.map = map;
        this.robots = robots;
        this.collisionManager = new CollisionManager();
        this.dispatcher = dispatcher;
        this.coordinationPolicy = coordinationPolicy;
    }

    /**
     * Runs a tick of the simulation
     *
     * <p>During each tick, the engine:
     * <ul>
     *  <li>Collects movement intentions from all robots</li>
     *  <li>Resolves conflicts and collisions using the {@link CollisionManager}</li>
     *  <li>Commits the approved movements by updating the position state of each robot</li>
     * </ul>
     * </p>
     */
    public void tick() {
        // Collecting initial move intentions from all robots
        MoveIntention[] intentions = collectIntentions();

        // Resolving conflicts/collisions and finalizing move intentions for all robots
        MoveIntention[] finalMoveIntentions = collisionManager.resolveConflicts(intentions);

        // Commiting move intentions by updating all robot states
        updateRobotStates(finalMoveIntentions);

        incrementTickCounter();
    }

    /**
     * Collects move intentions for all robots in the simulation
     * @return an array of MoveIntentions, one for each robot in the simulation
     */
    private MoveIntention[] collectIntentions() {
        MoveIntention[] intentions = new MoveIntention[robots.length];

        // Collection MoveIntentions for each robot
        for (int i = 0; i < robots.length; i++) {
            intentions[i] = robots[i].getNextMove(map);
        }

        return intentions;
    }

    /**
     * Updates states for all robots based on commited move intentions
     * @param intentions an array of finalized MoveIntentions that are ready to be commited for every robot
     */
    private void updateRobotStates(MoveIntention[] intentions) {
        // Move all robots to `From` tile in their move intentions
        for (MoveIntention intention : intentions) {
            Robot robot = intention.getRobot();
            Vector2D newPosition = intention.getFromTile().getPosition();
            robot.setPosition(newPosition);
        }
    }

    /**
     * Increments the tick counter for the simulation engine
     */
    private void incrementTickCounter() {
        tickCounter++;
    }
}
