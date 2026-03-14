package com.openrobotics;

import com.openrobotics.simulationcore.SimulationEngine;

/**
 * Lightweight application-level state holder shared across screens.
 * Holds the active {@link SimulationEngine} instance and the path of the
 * last loaded config file so the simulation can be restarted from scratch.
 */
public final class AppState {

    private static SimulationEngine engine;
    private static String configPath;
    private static int canvasWidthTiles  = 30;
    private static int canvasHeightTiles = 30;

    private AppState() {}

    public static SimulationEngine getEngine()               { return engine; }
    public static void setEngine(SimulationEngine e)         { engine = e; }
    public static boolean hasEngine()                        { return engine != null; }

    public static String getConfigPath()                     { return configPath; }
    public static void setConfigPath(String path)            { configPath = path; }
    public static boolean hasConfigPath()                    { return configPath != null && !configPath.isBlank(); }

    public static int  getCanvasWidthTiles()                 { return canvasWidthTiles; }
    public static int  getCanvasHeightTiles()                { return canvasHeightTiles; }
    public static void setCanvasDimensions(int w, int h)     { canvasWidthTiles = Math.max(1, w); canvasHeightTiles = Math.max(1, h); }
    /** Legacy single-axis accessor — returns the larger of width/height. */
    public static int  getCanvasTiles()                      { return Math.max(canvasWidthTiles, canvasHeightTiles); }

    public static void clear() {
        engine = null;
        configPath = null;
        // canvasTiles intentionally preserved — it is a UI preference, not part of the loaded config
    }
}
