package com.openrobotics.integration;

import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.navigation.GreedyNavigationStrategy;
import com.openrobotics.simulationcore.SimulationEngine;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared helper base for simulation integration tests.
 *
 * <p>Provides concise fixture utilities for common position/robot setup and deterministic tick
 * advancement patterns used across integration suites.</p>
 */
public abstract class SimulationIntegrationTestSupport {

    /**
     * Creates a position vector with integer tile coordinates.
     */
    protected Vector2D pos(int x, int y) {
        return new Vector2D(x, y);
    }

    /**
     * Creates a robot with greedy navigation and deterministic seed.
     */
    protected Robot greedyRobot(String name, int x, int y, long seed) {
        Robot robot = new Robot(name, pos(x, y));
        robot.setNav(new GreedyNavigationStrategy(seed));
        return robot;
    }

    /**
     * Creates an ID-stable robot with greedy navigation and deterministic seed.
     */
    protected Robot greedyRobot(UUID id, String name, int x, int y, long seed) {
        Robot robot = new Robot(id, name, pos(x, y));
        robot.setNav(new GreedyNavigationStrategy(seed));
        return robot;
    }

    /**
     * Advances simulation by a fixed number of ticks.
     */
    protected void runTicks(SimulationEngine engine, int count) {
        for (int i = 0; i < count; i++) {
            engine.tick();
        }
    }

    /**
     * Advances simulation until condition passes or tick budget is exhausted.
     *
     * @param engine engine to advance
     * @param condition success predicate checked before each tick
     * @param maxTicks maximum number of ticks to execute
     * @param failureMessage assertion message used if condition never becomes true
     */
    protected void runTicksUntil(SimulationEngine engine, BooleanSupplier condition, int maxTicks, String failureMessage) {
        for (int i = 0; i < maxTicks; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            engine.tick();
        }

        if (!condition.getAsBoolean()) {
            fail(failureMessage);
        }
    }
}
