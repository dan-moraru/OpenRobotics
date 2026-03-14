package com.openrobotics;

import com.openrobotics.simulationcore.SimulationEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SimulationEngineConfigLoadTest {

    @Test
    void loadsJsonScenarioFromRepositoryRoot() {
        Path configPath = Path.of("..", "test_scenario.json").toAbsolutePath().normalize();

        SimulationEngine engine = new SimulationEngine(configPath.toString());

        assertNull(engine.getInitError(), "Config load should not produce an initialization error.");
        assertNotNull(engine.getMap(), "Config load should create a map.");
        assertEquals(20, engine.getMap().getEntities().size(), "Config load should materialize all scenario entities.");
    }
}