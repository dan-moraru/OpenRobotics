package com.openrobotics.integration;

import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.navigation.GreedyNavigationStrategy;
import com.openrobotics.simulationcore.SimulationEngine;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;

public abstract class SimulationIntegrationTestSupport {

    protected Vector2D pos(int x, int y) {
        return new Vector2D(x, y);
    }

    protected Robot greedyRobot(String name, int x, int y, long seed) {
        Robot robot = new Robot(name, pos(x, y));
        robot.setNav(new GreedyNavigationStrategy(seed));
        return robot;
    }

    protected Robot greedyRobot(UUID id, String name, int x, int y, long seed) {
        Robot robot = new Robot(id, name, pos(x, y));
        robot.setNav(new GreedyNavigationStrategy(seed));
        return robot;
    }

    protected void runTicks(SimulationEngine engine, int count) {
        for (int i = 0; i < count; i++) {
            engine.tick();
        }
    }

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
