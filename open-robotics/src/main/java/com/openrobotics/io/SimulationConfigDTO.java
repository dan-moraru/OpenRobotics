package com.openrobotics.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.openrobotics.task.TaskStatus;

import java.util.List;
import java.util.UUID;

/** JSON schema for simulation config files; sections cover config, map, entities, tasks, coordination, and simulation state. */
public class SimulationConfigDTO {
    public ConfigSection config;
    public MapSection map;
    public EntitiesSection entities;
    public List<TaskDTO> tasks;
    public SimStateSection simulation;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public CoordinationSection coordination;

    public static class ConfigSection {
        public UUID runId;
        public String runName;
        public int    tickMs              = 100;
        public int    maxTicks            = 30000;
        public long   seed                = 42;
        public float  batteryCapacity     = 100.0f;
        public float  lowBatteryThreshold = 20.0f;
        public float  chargePerTick       = 5.0f;
        public float  energyPerMove       = 1.0f;
        public int    loadingTicks        = 1;
        public int    unloadingTicks      = 1;
        public int    maxTasks            = 10;
        /** Whether tasks come from rack box counts and dropoff pools instead of random generation up to {@code maxTasks}. */
        public boolean manualTaskAssignment = false;
    }

    public static class MapSection {
        public UUID mapId;
        public int width;
        public int height;
        public List<TileDTO> tiles;
    }

    public static class EntitiesSection {
        public List<RobotDTO> robots;
        public List<MapEntityDTO> stations;
        public List<RackDTO> racks;
        public List<MapEntityDTO> obstacles;
    }

    public static class SimStateSection {
        public int tick;
        public boolean isRunning;
        public double speedMultiplier;
    }

    public static class CoordinationSection {
        public String type; // e.g "TRAFFIC_RULES" or "RESERVATION_K"
        public Integer k;   // only used by RESERVATION_K policy
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
        public String type; // "CHARGING", "DELIVERY", or null for old configs
    }

    public static class RackDTO extends MapEntityDTO {
        public int boxCount = 1;
        public List<String> validDropoffIds; // UUID strings, null = all stations valid
        public boolean manualDropoffAssignment = false;
    }

    public static class RobotDTO extends MapEntityDTO {
        public float battery;
        public String state; // Maps to RobotState enum
        public String navigationStrategy;
        public String sensorStrategy;
        public int stuckTicks;
    }

    public static class TaskDTO {
        public long id;
        public Vector2DDTO pickupLocation;
        public Vector2DDTO dropoffLocation;
        public int priority;
        public TaskStatus status;
    }
}
