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

    private AppState() {}

    public static SimulationEngine getEngine()         { return engine; }
    public static void setEngine(SimulationEngine e)   { engine = e; }
    public static boolean hasEngine()                  { return engine != null; }

    public static String getConfigPath()               { return configPath; }
    public static void setConfigPath(String path)      { configPath = path; }
    public static boolean hasConfigPath()              { return configPath != null && !configPath.isBlank(); }

    public static void clear() {
        engine = null;
        configPath = null;
    }
}
