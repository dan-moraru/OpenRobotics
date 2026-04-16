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
     * Verifies {@link AppState#clear()} removes engine/config/output paths while preserving previously set
     * canvas dimensions.
     */
    @Test
    void clear_resets_engine_and_paths_but_keeps_canvas_dimensions() {
        SimulationEngine engine = new SimulationEngine(
                new Map(1, 1),
                new Robot[0],
                new Dispatcher(),
                CoordinationPolicy.noOp()
        );

        AppState.setEngine(engine);
        AppState.setConfigPath("sample.json");
        AppState.setEditorBaselinePath("baseline.json");
        AppState.setSimulationConsoleText("console");
        AppState.setSimulationLogText("log");
        AppState.setSimulationLogCursor(42);
        AppState.setCanvasDimensions(12, 8);

        AppState.clear();

        assertNull(AppState.getEngine());
        assertNull(AppState.getConfigPath());
        assertNull(AppState.getEditorBaselinePath());
        assertNull(AppState.getSimulationConsoleText());
        assertNull(AppState.getSimulationLogText());
        assertEquals(0, AppState.getSimulationLogCursor());
        assertFalse(AppState.hasEngine());
        assertFalse(AppState.hasConfigPath());
        assertFalse(AppState.hasEditorBaselinePath());
        assertFalse(AppState.hasSimulationConsoleText());
        assertFalse(AppState.hasSimulationLogText());
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
    void engine_and_path_flags_follow_assigned_values() {
        SimulationEngine engine = new SimulationEngine(
                new Map(1, 1),
                new Robot[0],
                new Dispatcher(),
                CoordinationPolicy.noOp()
        );

        AppState.setEngine(engine);
        AppState.setConfigPath("config.json");
        AppState.setEditorBaselinePath("baseline.json");
        AppState.setSimulationConsoleText("console");
        AppState.setSimulationLogText("log");
        AppState.setSimulationLogCursor(7);

        assertSame(engine, AppState.getEngine());
        assertEquals("config.json", AppState.getConfigPath());
        assertEquals("baseline.json", AppState.getEditorBaselinePath());
        assertEquals("console", AppState.getSimulationConsoleText());
        assertEquals("log", AppState.getSimulationLogText());
        assertEquals(7, AppState.getSimulationLogCursor());
        assertTrue(AppState.hasEngine());
        assertTrue(AppState.hasConfigPath());
        assertTrue(AppState.hasEditorBaselinePath());
        assertTrue(AppState.hasSimulationConsoleText());
        assertTrue(AppState.hasSimulationLogText());
    }
}
