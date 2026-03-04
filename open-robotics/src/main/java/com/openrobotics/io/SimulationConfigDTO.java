package com.openrobotics.io;

import com.openrobotics.map.Vector2D;

import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object (DTO) used for serializing and deserializing the state
 * of the OpenRobotics warehouse simulation.
 * <p>This class acts as a schema-matching container for Jackson-based JSON parsing.
 * It decouples the persistence format from the core domain logic, allowing
 * the simulation state to be saved to or loaded from a file without directly
 * exposing internal simulation logic.</p>
 *
 * <p>The structure is divided into several logical sections:</p>
 * <ul>
 * <li><b>Config:</b> Global metadata such as run name, tick timing, and random seed.</li>
 * <li><b>Map:</b> Grid dimensions and specific tile states (e.g., occupancy).</li>
 * <li><b>Entities:</b> Collections of physical objects including Robots, Stations, Racks, and Obstacles.</li>
 * <li><b>Tasks:</b> A list of pending or active work assignments.</li>
 * <li><b>Coordination:</b> Configuration for traffic and collision avoidance policies.</li>
 * <li><b>Simulation:</b> The current runtime state, including the tick counter and execution status.</li>
 * </ul>
 * @see com.openrobotics.io.ConfigLoader
 * @see com.openrobotics.simulationcore.SimulationEngine
 */
public class SimulationConfigDTO {
    public ConfigSection config;
    public MapSection map;
    public EntitiesSection entities;
    public List<TaskDTO> tasks;
    public SimStateSection simulation;
    public CoordinationSection coordination;

    public static class ConfigSection {
        public String runName;
        public int tickMs;
        public int maxTicks;
        public long seed;
    }

    public static class MapSection {
        public int width;
        public int height;
        public List<TileDTO> tiles;
    }

    public static class EntitiesSection {
        public List<RobotDTO> robots;
        public List<MapEntityDTO> stations;
        public List<MapEntityDTO> racks;
        public List<MapEntityDTO> obstacles;
    }

    public static class SimStateSection {
        public int tick;
        public boolean isRunning;
        public double speedMultiplier;
    }

    public static class CoordinationSection {
        public String type; // e.g "TRAFFIC_RULES" or "RESERVATION_K"
        public Integer k;   // for ReservationK, might change that
        public List<Vector2DDTO> intersections; // for TrafficRules
    }

    public static class Vector2DDTO {
        public int x;
        public int y;

        public Vector2DDTO() {}

        public Vector2DDTO(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class TileDTO {
        public int x;
        public int y;
        public boolean isOccupied;
    }

    public static class MapEntityDTO {
        public UUID id;
        public String name;
        public Vector2DDTO position;
    }

    public static class RobotDTO extends MapEntityDTO {
        public float battery;
        public String state; // Maps to RobotState enum
        public String navigationStrategy;
        public int stuckTicks;
    }

    public static class TaskDTO {
        public int id;
        public Vector2DDTO pickupLocation;
        public Vector2DDTO dropoffLocation;
        public int priority;
        public String status;
    }
}