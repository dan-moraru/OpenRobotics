package com.openrobotics.simulationcore;

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
    void loadsJsonScenarioFromRepositoryRoot() throws IOException {
        Path source = Path.of("src", "test", "resources", "com", "openrobotics", "io", "test_scenario.json");
        Path configPath = Files.createTempFile("test_scenario", ".json");
        Files.copy(source, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        SimulationEngine engine = new SimulationEngine(configPath.toString());

        assertNull(engine.getInitError(), "Config load should not produce an initialization error.");
        assertNotNull(engine.getMap(), "Config load should create a map.");
        assertEquals(20, engine.getMap().getEntities().size(), "Config load should materialize all scenario entities.");
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
