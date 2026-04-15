package com.openrobotics.simulationcore;

import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration-style tests for {@link SimulationEngine} JSON load/save behavior.
 *
 * <p>This suite verifies successful config loading from test resources, backward-compatible loading
 * when optional coordination sections are absent, and save-time omission of coordination metadata
 * when the engine runs with no-op policy semantics.</p>
 */
class SimulationEngineConfigLoadTest {

    /**
     * Verifies a repository-backed scenario JSON can be copied to a temp file and loaded into a
     * fully initialized engine instance.
     *
     * @throws IOException if test resource copy or file operations fail
     */
    @Test
    void loadsJsonScenarioFromRepositoryRoot() throws IOException {
        Path configPath = Files.createTempFile("test_scenario", ".json");
        try (InputStream in = SimulationEngineConfigLoadTest.class.getResourceAsStream(
                "/com/openrobotics/io/test_scenario.json")) {
            assertNotNull(in,
                    "test_scenario.json must live under src/test/resources/com/openrobotics/io/ (classpath).");
            Files.copy(in, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        SimulationEngine engine = new SimulationEngine(configPath.toString());

        assertNull(engine.getInitError(), "Config load should not produce an initialization error.");
        assertNotNull(engine.getMap(), "Config load should create a map.");
        assertEquals(20, engine.getMap().getEntities().size(), "Config load should materialize all scenario entities.");
    }

    /**
     * Verifies configs without a {@code coordination} section still load successfully and default
     * to no-op coordination behavior.
     *
     * @throws IOException if temporary config file operations fail
     */
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

    /**
     * Verifies saving an engine with no coordination policy omits the coordination JSON block.
     *
     * @throws IOException if save/read file operations fail
     */
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

    /**
     * Finds a robot by name in a possibly sparse robot array.
     *
     * @return matching robot or {@code null} if no match exists
     */
    private Robot findRobotByName(Robot[] robots, String name) {
        for (Robot robot : robots) {
            if (robot != null && name.equals(robot.getName())) {
                return robot;
            }
        }
        return null;
    }
}
