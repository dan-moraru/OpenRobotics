package com.openrobotics;

import com.openrobotics.simulationcore.SimulationEngine;

/** Lightweight application-level state holder; shares the active engine and last-loaded config path across screens. */
public final class AppState {

    private static SimulationEngine engine;
    private static String configPath;
    private static int canvasWidthTiles  = 15;
    private static int canvasHeightTiles = 15;
    private static int simulationTick = 0;

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
    
    /**
     * Returns the larger of the canvas width and height as a single tile dimension.
     * Legacy accessor — prefer {@link #getCanvasWidthTiles()} and {@link #getCanvasHeightTiles()} for new code.
     *
     * @return the larger of {@code canvasWidthTiles} and {@code canvasHeightTiles}
     */
    public static int  getCanvasTiles()                      { return Math.max(canvasWidthTiles, canvasHeightTiles); }

    public static int  getSimulationTick()                     { return simulationTick; }
    public static void setSimulationTick(int tick)           { simulationTick = tick; }

    public static void clear() {
        engine = null;
        configPath = null;
        simulationTick = 0;
    }
}
