package com.openrobotics.simulationcore;

import com.openrobotics.io.ConfigLoader;
import com.openrobotics.io.SimulationConfigDTO;
import com.openrobotics.map.*;
import com.openrobotics.robot.*;
import com.openrobotics.task.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SimulationEngine {
    private int tickCounter;
    private boolean running; // tracks if the simulation is still running
    private final CollisionManager collisionManager;
    private Map map;
    private Dispatcher dispatcher;
    private CoordinationPolicy coordinationPolicy;

    // Added these for config file loading/saving
    private String runName;
    private int tickMs;
    private int maxTicks;
    private long seed;

    public SimulationEngine(String configFilePath) {
        collisionManager = new CollisionManager();

        if (configFilePath != null) {
            configInitialization(configFilePath);
        } else {
            // Default initialization if no file is provided
            this.tickCounter = 0;
            this.running = false;
        }
    }

    /**
     * Initializes the simulation based on the JSON config file.
     * Does this by parsing the JSON file to the DTO classes, and from the DTO classes, we correctly set up the core simulation classes.
     * @param path the JSON config file path
     */
    private void configInitialization(String path) {
        try {
            // Load the DTO
            SimulationConfigDTO dto = ConfigLoader.load(path, SimulationConfigDTO.class);

            // Initialize the Map
            this.map = new Map(dto.map.width, dto.map.height);

            // Update tile occupancy from the JSON
            if (dto.map.tiles != null) {
                for (SimulationConfigDTO.TileDTO tileDto : dto.map.tiles) {
                    Tile tile = this.map.getTile(tileDto.x, tileDto.y);
                    if (tile != null) {
                        tile.setOccupied(tileDto.isOccupied);
                    }
                }
            }

            // Initialize Entities (Robots, Stations, Obstacles, etc.)
            // robots
            for (SimulationConfigDTO.RobotDTO rDto : dto.entities.robots) {
                Vector2D pos = new Vector2D(rDto.position.x, rDto.position.y);
                Robot robot = new Robot(rDto.id, rDto.name, pos);

                robot.setBattery(rDto.battery);
                robot.setStuckTicks(rDto.stuckTicks);
                robot.setState(RobotState.valueOf(rDto.state));

                if (rDto.navigationStrategy.equals("GREEDYNAVIGATIONSTRATEGY")) {
                    robot.setNav(new GreedyNavigationStrategy());
                } // greedy strategy for now to test

                this.map.addEntity(robot);
            }

            // Generic MapEntities (Stations, Racks, Obstacles)
            addEntitiesToMap(dto.entities.stations);
            addEntitiesToMap(dto.entities.racks);
            addEntitiesToMap(dto.entities.obstacles);

            // Initialize Dispatcher and Tasks
            this.dispatcher = new Dispatcher();
            if (dto.tasks != null) {
                for (SimulationConfigDTO.TaskDTO tDto : dto.tasks) {
                    Task task = new Task(
                            tDto.id,
                            new Vector2D(tDto.pickupLocation.x, tDto.pickupLocation.y),
                            new Vector2D(tDto.dropoffLocation.x, tDto.dropoffLocation.y),
                            tDto.priority
                    );
                    task.setStatus(tDto.status);
                    this.dispatcher.addTask(task);
                }
            }

            // Setup Coordination Policy
            if (dto.coordination != null) {
                if ("RESERVATION_K".equals(dto.coordination.type)) {
                    this.coordinationPolicy = new ReservationKPolicy(dto.coordination.k);

                } else if ("TRAFFIC_RULES".equals(dto.coordination.type)) {
                    Set<Tile> intersectionTiles = new HashSet<>();
                    for (SimulationConfigDTO.Vector2DDTO v : dto.coordination.intersections) {
                        intersectionTiles.add(this.map.getTile(v.x, v.y));
                    }
                    this.coordinationPolicy = new TrafficRulesPolicy(intersectionTiles);
                }
            }

            // Set simulation state
            this.tickCounter = dto.simulation.tick;
            this.running = dto.simulation.isRunning;
            this.runName = dto.config.runName;
            this.tickMs = dto.config.tickMs;
            this.maxTicks = dto.config.maxTicks;
            this.seed = dto.config.seed;
            // this.speedMultiplier = dto.simulation.speedMultiplier; // not yet I believe

            // Test print, TODO: remove
            System.out.println("Simulation '" + dto.config.runName + "' loaded with "
                    + dto.entities.robots.size() + " robots and "
                    + (dto.tasks != null ? dto.tasks.size() : 0) + " tasks.");

        } catch (IOException e) {
            //TODO: send error message to the frontend
            System.err.println("Error: Could not initialize simulation from file: " + e.getMessage());
        }
    }

    /**
     * Helper to handle MapEntity loading
     * Generic MapEntity parser called in configInitialization
     */
    private void addEntitiesToMap(List<SimulationConfigDTO.MapEntityDTO> entityDtos) {
        if (entityDtos == null) return;

        for (SimulationConfigDTO.MapEntityDTO eDto : entityDtos) {
            Vector2D pos = new Vector2D(eDto.position.x, eDto.position.y);
            MapEntity entity = new MapEntity(eDto.id, eDto.name, pos);
            this.map.addEntity(entity);
        }
    }

    /**
     * Saves the simulation state into a new JSON config file
     * Does this by retrieving core simulation classes current state (fields, metadata, etc) and saves it to the DTO classes which are easily translated back to JSON format.
     * @param path the new JSON config file path
     */
    public void configSaving(String path) throws IOException {
        SimulationConfigDTO dto = new SimulationConfigDTO();

        // Config Metadata
        dto.config = new SimulationConfigDTO.ConfigSection();
        dto.config.runName = this.runName;
        dto.config.tickMs = this.tickMs;
        dto.config.maxTicks = this.maxTicks;
        dto.config.seed = this.seed;

        // Map Section
        dto.map = new SimulationConfigDTO.MapSection();
        dto.map.width = map.getWidth();
        dto.map.height = map.getHeight();
        dto.map.tiles = new ArrayList<>();
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                if (tile.isOccupied()) {
                    SimulationConfigDTO.TileDTO tDto = new SimulationConfigDTO.TileDTO();
                    tDto.x = x;
                    tDto.y = y;
                    tDto.isOccupied = true;
                    dto.map.tiles.add(tDto);
                }
            }
        }

        // Entities Section
        dto.entities = new SimulationConfigDTO.EntitiesSection();
        dto.entities.robots = new ArrayList<>();
        dto.entities.stations = new ArrayList<>();
        dto.entities.racks = new ArrayList<>();
        dto.entities.obstacles = new ArrayList<>();

        for (MapEntity entity : map.getEntities()) {
            if (entity instanceof Robot) {
                dto.entities.robots.add(mapToRobotDTO((Robot) entity));
            } else {
                SimulationConfigDTO.MapEntityDTO eDto = mapToEntityDTO(entity);
                String name = entity.getName().toLowerCase();

                // Improved classification logic
                if (name.contains("charge") || name.contains("station")) {
                    dto.entities.stations.add(eDto);
                } else if (name.contains("rack")) {
                    dto.entities.racks.add(eDto);
                } else {
                    dto.entities.obstacles.add(eDto);
                }
            }
        }

        // Tasks Section
        dto.tasks = new ArrayList<>();
        if (this.dispatcher != null) {
            for (Task task : this.dispatcher.getAllTasks()) {
                SimulationConfigDTO.TaskDTO tDto = new SimulationConfigDTO.TaskDTO();
                tDto.id = task.getId();
                tDto.pickupLocation = new SimulationConfigDTO.Vector2DDTO((int)task.getPickup().getX(), (int)task.getPickup().getY());
                tDto.dropoffLocation = new SimulationConfigDTO.Vector2DDTO((int)task.getDropoff().getX(), (int)task.getDropoff().getY());
                tDto.priority = task.getPriority();
                tDto.status = task.getStatus();
                dto.tasks.add(tDto);
            }
        }

        // Simulation State
        dto.simulation = new SimulationConfigDTO.SimStateSection();
        dto.simulation.tick = this.tickCounter;
        dto.simulation.isRunning = this.running;
        dto.simulation.speedMultiplier = 1.0; // For now but needs to be in its own field

        // Coordination Policy
        dto.coordination = new SimulationConfigDTO.CoordinationSection();
        if (coordinationPolicy instanceof ReservationKPolicy) {
            dto.coordination.type = "RESERVATION_K";
            dto.coordination.k = ((ReservationKPolicy) coordinationPolicy).getK();
        } else if (coordinationPolicy instanceof TrafficRulesPolicy) {
            dto.coordination.type = "TRAFFIC_RULES";
            dto.coordination.intersections = new ArrayList<>();
            // map intersection tiles?
        }

        ConfigLoader.save(path, dto);
    }


    /**
     * Helper function to map robot to DTO when saving the simulation state
     */
    private SimulationConfigDTO.RobotDTO mapToRobotDTO(Robot robot) {
        SimulationConfigDTO.RobotDTO rDto = new SimulationConfigDTO.RobotDTO();
        rDto.id = robot.getId();
        rDto.name = robot.getName();
        rDto.position = new SimulationConfigDTO.Vector2DDTO();
        rDto.position.x = robot.getPosition().getX();
        rDto.position.y = robot.getPosition().getY();
        rDto.battery = robot.getBattery();
        rDto.state = robot.getState().name();
        rDto.stuckTicks = robot.getStuckTicks();
        rDto.navigationStrategy = (robot.getNav() != null) ? robot.getNav().getClass().getSimpleName().toUpperCase() : "NONE";
        return rDto;
    }

    /**
     * Helper function to map MapEntities to DTO when saving the simulation state
     */
    private SimulationConfigDTO.MapEntityDTO mapToEntityDTO(MapEntity entity) {
        SimulationConfigDTO.MapEntityDTO eDto = new SimulationConfigDTO.MapEntityDTO();
        eDto.id = entity.getId();
        eDto.name = entity.getName();
        eDto.position = new SimulationConfigDTO.Vector2DDTO();
        eDto.position.x = entity.getPosition().getX();
        eDto.position.y = entity.getPosition().getY();
        return eDto;
    }

    public Map getMap() {
        return map;
    }

    // Runs a tick of the simulation
    public void tick() {
        // TODO
    }

    private void incrementTickCounter() {
        tickCounter++;
    }

    // Collects move intentions for all robots in the simulation
    private MoveIntention[] collectIntentions() {
        // TODO

        return null;
    }

    // Commits move intentions for all robots
    private void CommitMoveIntentions() {
        // TODO
    }

    // Updates states for all robots based on commited move intentions
    private void updateRobotStates() {
        // TODO
    }


}
