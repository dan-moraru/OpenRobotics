package com.openrobotics;

import com.openrobotics.map.Map;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.CoordinationPolicy;
import com.openrobotics.simulationcore.Dispatcher;
import com.openrobotics.simulationcore.SimulationEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for global {@link AppState} storage semantics.
 *
 * <p>This suite verifies clear/reset behavior, canvas-dimension clamping rules, and consistency of
 * engine/config-path presence flags with assigned values.</p>
 */
public class AppStateTest {

    /**
     * Restores shared static app state after each test to avoid cross-test leakage.
     */
    @AfterEach
    void resetSharedState() {
        AppState.clear();
        AppState.setCanvasDimensions(30, 30);
    }

    /**
     * Verifies {@link AppState#clear()} removes engine/config path while preserving previously set
     * canvas dimensions.
     */
    @Test
    void clear_resets_engine_and_config_path_but_keeps_canvas_dimensions() {
        SimulationEngine engine = new SimulationEngine(
                new Map(1, 1),
                new Robot[0],
                new Dispatcher(),
                CoordinationPolicy.noOp()
        );

        AppState.setEngine(engine);
        AppState.setConfigPath("sample.json");
        AppState.setCanvasDimensions(12, 8);

        AppState.clear();

        assertNull(AppState.getEngine());
        assertNull(AppState.getConfigPath());
        assertFalse(AppState.hasEngine());
        assertFalse(AppState.hasConfigPath());
        assertEquals(12, AppState.getCanvasWidthTiles());
        assertEquals(8, AppState.getCanvasHeightTiles());
    }

    /**
     * Verifies canvas dimensions are clamped to minimum 1 and aggregate tile accessor returns the
     * larger dimension.
     */
    @Test
    void setCanvasDimensions_clamps_values_and_getCanvasTiles_returns_max_dimension() {
        AppState.setCanvasDimensions(-5, 0);

        assertEquals(1, AppState.getCanvasWidthTiles());
        assertEquals(1, AppState.getCanvasHeightTiles());
        assertEquals(1, AppState.getCanvasTiles());

        AppState.setCanvasDimensions(14, 9);

        assertEquals(14, AppState.getCanvasWidthTiles());
        assertEquals(9, AppState.getCanvasHeightTiles());
        assertEquals(14, AppState.getCanvasTiles());
    }

    /**
     * Verifies engine/config assignment updates both direct getters and presence flags.
     */
    @Test
    void engine_and_config_path_flags_follow_assigned_values() {
        SimulationEngine engine = new SimulationEngine(
                new Map(1, 1),
                new Robot[0],
                new Dispatcher(),
                CoordinationPolicy.noOp()
        );

        AppState.setEngine(engine);
        AppState.setConfigPath("config.json");

        assertSame(engine, AppState.getEngine());
        assertEquals("config.json", AppState.getConfigPath());
        assertTrue(AppState.hasEngine());
        assertTrue(AppState.hasConfigPath());
    }
}
