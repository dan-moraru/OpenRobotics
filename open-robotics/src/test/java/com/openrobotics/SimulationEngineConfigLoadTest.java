package com.openrobotics;

import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.SimulationEngine;
import com.openrobotics.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineConfigLoadTest {

    @Test
    void loadsJsonScenarioFromRepositoryRoot() {
        Path configPath = Path.of("..", "test_scenario.json").toAbsolutePath().normalize();

        SimulationEngine engine = new SimulationEngine(configPath.toString());

        assertNull(engine.getInitError(), "Config load should not produce an initialization error.");
        assertNotNull(engine.getMap(), "Config load should create a map.");
        assertEquals(20, engine.getMap().getEntities().size(), "Config load should materialize all scenario entities.");
    }

    @Test
    void rerouteSuccessDemoShowsAVisibleDetourWithoutRequeueingTheTask() {
        Path configPath = Path.of("..", "deadlock_recovery_reroute_success_demo.json").toAbsolutePath().normalize();

        SimulationEngine engine = new SimulationEngine(configPath.toString());

        assertNull(engine.getInitError(), "Reroute success demo should load without initialization errors.");
        Robot recoveredBot = findRobotByName(engine.getRobots(), "RecoveredBot");
        assertNotNull(recoveredBot, "The reroute success demo should include RecoveredBot.");

        for (int i = 0; i < 5; i++) {
            engine.tick();
        }

        assertEquals("MOVING", recoveredBot.getStatus());
        assertTrue(recoveredBot.hasRerouteAttemptedForCurrentTask());
        assertEquals(0, engine.getDispatcher().getPendingTaskCount());

        for (int i = 0; i < 3; i++) {
            engine.tick();
        }

        assertTrue(recoveredBot.getPosition().getY() == 1 || recoveredBot.getPosition().getX() > 3,
                "RecoveredBot should visibly detour around the blocked tile after rerouting.");
        assertEquals(0, engine.getDispatcher().getPendingTaskCount());
    }

    @Test
    void rerouteFallbackDemoRequeuesTheTaskAfterTheSecondDeadlock() {
        Path configPath = Path.of("..", "deadlock_recovery_reroute_fallback_demo.json").toAbsolutePath().normalize();

        SimulationEngine engine = new SimulationEngine(configPath.toString());

        assertNull(engine.getInitError(), "Reroute fallback demo should load without initialization errors.");
        Robot recoveredBot = findRobotByName(engine.getRobots(), "RecoveredBot");
        assertNotNull(recoveredBot, "The reroute fallback demo should include RecoveredBot.");

        for (int i = 0; i < 10; i++) {
            engine.tick();
        }

        assertEquals("IDLE", recoveredBot.getStatus());
        assertEquals(1, engine.getDispatcher().getPendingTaskCount());
        assertEquals(TaskStatus.PENDING, engine.getDispatcher().getAllTasks().get(0).getStatus());
    }

    @Test
    void loadsConfigWithoutCoordinationSectionUsingNoOpPolicy() throws IOException {
        Path configPath = Files.createTempFile("simulation-no-coordination", ".json");
        Files.writeString(configPath, """
                {
                  "config": {
                    "runName": "no_coordination",
                    "tickMs": 100,
                    "maxTicks": 5000,
                    "seed": 42
                  },
                  "map": {
                    "width": 4,
                    "height": 4,
                    "tiles": []
                  },
                  "entities": {
                    "robots": [],
                    "stations": [],
                    "racks": [],
                    "obstacles": []
                  },
                  "tasks": [],
                  "simulation": {
                    "tick": 0,
                    "isRunning": false,
                    "speedMultiplier": 1.0
                  }
                }
                """);

        SimulationEngine engine = new SimulationEngine(configPath.toString());

        assertNull(engine.getInitError(), "Config load should allow a missing coordination section.");
        assertNotNull(engine.getMap(), "Config load should still create a map.");
        assertDoesNotThrow(engine::tick, "Engine should tick with the no-op coordination policy.");
    }

    @Test
    void savingWithNoOpPolicyOmitsCoordinationSection() throws IOException {
        SimulationEngine engine = new SimulationEngine(
                new Map(3, 3),
                new Robot[0],
                new Dispatcher(),
                null
        );
        Path outputPath = Files.createTempFile("simulation-saved-no-coordination", ".json");

        engine.configSaving(outputPath.toString());

        String savedJson = Files.readString(outputPath);
        assertFalse(savedJson.contains("\"coordination\""),
                "Saved JSON should omit the coordination section when using the no-op policy.");
    }

    private Robot findRobotByName(Robot[] robots, String name) {
        for (Robot robot : robots) {
            if (robot != null && name.equals(robot.getName())) {
                return robot;
            }
        }
        return null;
    }
}
