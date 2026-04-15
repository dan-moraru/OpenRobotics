package com.openrobotics.integration.config;

import com.openrobotics.io.ConfigLoader;
import com.openrobotics.io.SimulationConfigDTO;
import com.openrobotics.simulationcore.SimulationEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Negative integration tests for configuration loading and simulation initialization.
 *
 * <p>This suite validates failure behavior when configuration files are malformed, incomplete, or
 * semantically invalid. Assertions focus on defensive initialization outcomes (captured init error,
 * unusable engine state) and expected exceptions from loader APIs.</p>
 */
public class SimulationConfigNegativeIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies malformed JSON produces init error state and prevents engine ticking.
     *
     * @throws Exception if test file setup fails
     */
    @Test
    void malformed_json_file_sets_init_error_and_engine_cannot_tick() throws Exception {
        Path path = tempDir.resolve("malformed.json");
        Files.writeString(path, "{ this is not valid json");

        SimulationEngine engine = new SimulationEngine(path.toString());

        assertNull(engine.getMap());
        assertNotNull(engine.getInitError());
        assertThrows(IllegalStateException.class, engine::tick);
    }

    /**
     * Verifies missing required top-level config section causes initialization failure and blocks
     * simulation advancement.
     *
     * @throws Exception if test file setup fails
     */
    @Test
    void missing_required_config_section_sets_init_error_and_engine_cannot_tick() throws Exception {
        Path path = tempDir.resolve("missing-config.json");
        Files.writeString(path, """
                {
                  "map": { "width": 2, "height": 2, "tiles": [] },
                  "entities": { "robots": [], "stations": [], "racks": [], "obstacles": [] },
                  "tasks": [],
                  "simulation": { "tick": 0, "isRunning": false, "speedMultiplier": 1.0 }
                }
                """);

        SimulationEngine engine = new SimulationEngine(path.toString());

        assertNull(engine.getMap());
        assertNotNull(engine.getInitError());
        assertTrue(engine.getInitError().contains("Could not initialize simulation"));
        assertThrows(IllegalStateException.class, engine::tick);
    }

    /**
     * Verifies invalid robot enum/state values in config surface an initialization error.
     *
     * @throws Exception if test file setup fails
     */
    @Test
    void invalid_robot_state_in_config_sets_init_error() throws Exception {
        Path path = tempDir.resolve("invalid-state.json");
        Files.writeString(path, """
                {
                  "config": { "runName": "bad-state", "tickMs": 50, "maxTicks": 100, "seed": 1 },
                  "map": { "width": 2, "height": 2, "tiles": [] },
                  "entities": {
                    "robots": [
                      {
                        "id": "%s",
                        "name": "R1",
                        "position": { "x": 0, "y": 0 },
                        "battery": 100.0,
                        "state": "NOT_A_REAL_STATE",
                        "navigationStrategy": "GREEDY",
                        "sensorStrategy": "PROXIMITY",
                        "stuckTicks": 0
                      }
                    ],
                    "stations": [],
                    "racks": [],
                    "obstacles": []
                  },
                  "tasks": [],
                  "simulation": { "tick": 0, "isRunning": false, "speedMultiplier": 1.0 }
                }
                """.formatted(UUID.randomUUID()));

        SimulationEngine engine = new SimulationEngine(path.toString());

        assertNull(engine.getMap());
        assertNotNull(engine.getInitError());
        assertTrue(engine.getInitError().contains("IllegalArgumentException"));
    }

    /**
     * Verifies {@link ConfigLoader#load(String, Class)} throws for non-existent file paths.
     */
    @Test
    void configLoader_load_throws_for_missing_file() {
        Path missing = tempDir.resolve("missing-file.json");

        assertThrows(IOException.class, () -> ConfigLoader.load(missing.toString(), SimulationConfigDTO.class));
    }
}
