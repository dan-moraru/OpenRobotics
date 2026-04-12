package com.openrobotics.simulationcore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrobotics.db.model.SimLogRecord;
import com.openrobotics.db.model.SimulationRunRecord;
import com.openrobotics.db.recordbuilders.SimLogRecordBuilder;
import com.openrobotics.db.recordbuilders.SimulationRunRecordBuilder;
import com.openrobotics.io.ConfigLoader;
import com.openrobotics.io.SimulationConfigDTO;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.eventtypes.RobotEvent;
import com.openrobotics.logging.eventtypes.SimulationRunEvent;
import com.openrobotics.map.*;
import com.openrobotics.map.Map;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.robot.*;
import com.openrobotics.robot.navigation.BugNavigationStrategy;
import com.openrobotics.robot.navigation.GreedyNavigationStrategy;
import com.openrobotics.robot.navigation.RtaStarNavigationStrategy;
import com.openrobotics.robot.sensors.ProximitySensor;
import com.openrobotics.robot.sensors.RangeSensor;
import com.openrobotics.task.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.*;

/**
 * The SimulationEngine is the core component responsible for advancing the
 * simulation. It coordinates the progression of discrete simulation steps
 * ("ticks") and manages interactions between robots within the environment.
 * The engine maintains an internal tick counter that represents the number
 * of simulation steps that have been executed.
 */
public class SimulationEngine {
    private UUID runId; // unique identifier for the simulation run, useful for logging and tracking
    private static final int DEADLOCK_RECOVERY_THRESHOLD = 5;
    private int tickCounter;
    private boolean running; // tracks if the simulation is still running
    private Map map;
    private Robot[] robots;
    private CollisionManager collisionManager;
    private Dispatcher dispatcher;
    private CoordinationPolicy coordinationPolicy;

    private RobotConfig robotConfig = RobotConfig.defaults();

    // Added these for config file loading/saving
    private String runName;
    private int tickMs;
    private int maxTicks;
    private long seed;
    private boolean initialized;

    // Workload configuration for spawn-rate mode
    private String workloadMode;
    private int spawnRate; // tasks per minute  (ticks / 60 * spawnRate)
    private int maxTasks; // absolute cap on total tasks to generate
    private int tasksGenerated;
    private int ticksSinceLastSpawn;
    // set true when racks exist but none are reachable; prevents infinite non-termination in SPAWN_RATE
    private boolean spawnSourceExhausted = false;

    // Add a private variable to store initialization errors
    private String initError;

    /**
     * Constructs a new simulation engine instance
     * @param map the map representing the warehouse environment
     * @param robots the robots
     * @param dispatcher the task dispatcher loaded with tasks ready to be dispatched to robots
     * @param coordinationPolicy the set coordination policy between robots that is followed when moving around the map
     */
    public SimulationEngine(Map map, Robot[] robots, Dispatcher dispatcher, CoordinationPolicy coordinationPolicy) {
        this(map, robots, dispatcher, coordinationPolicy, "default_run", 100, 5000, 42);
    }

    /**
     * Constructs a new simulation engine instance with full configuration
     * @param map the map representing the warehouse environment
     * @param robots the robots
     * @param dispatcher the task dispatcher loaded with tasks ready to be dispatched to robots
     * @param coordinationPolicy the set coordination policy between robots that is followed when moving around the map
     * @param runName the name of the simulation run
     * @param tickMs milliseconds per tick
     * @param maxTicks maximum number of ticks before simulation stops
     * @param seed random seed for navigation strategies
     */
    public SimulationEngine(Map map, Robot[] robots, Dispatcher dispatcher, CoordinationPolicy coordinationPolicy,
                          String runName, int tickMs, int maxTicks, long seed) {
        this(map, robots, dispatcher, coordinationPolicy, runName, tickMs, maxTicks, seed, "FIXED_LIST", 10, 10);
    }

    /**
     * Constructs a new simulation engine instance with full configuration including workload parameters.
     *
     * @param map the map representing the warehouse environment
     * @param robots the robots
     * @param dispatcher the task dispatcher loaded with tasks ready to be dispatched to robots
     * @param coordinationPolicy the set coordination policy between robots that is followed when moving around the map
     * @param runName the name of the simulation run
     * @param tickMs milliseconds per tick
     * @param maxTicks maximum number of ticks before simulation stops
     * @param seed random seed for navigation strategies
     * @param workloadMode "FIXED_LIST" or "SPAWN_RATE"
     * @param spawnRate tasks per minute (only used for SPAWN_RATE)
     * @param maxTasks absolute cap on total tasks to generate (applies to both modes)
     */
    public SimulationEngine(Map map, Robot[] robots, Dispatcher dispatcher, CoordinationPolicy coordinationPolicy,
                          String runName, int tickMs, int maxTicks, long seed,
                          String workloadMode, int spawnRate, int maxTasks) {

        this.runId = UUID.randomUUID();
        this.tickCounter = 0;
        this.running = false;
        this.map = map;
        this.robots = robots;
        this.collisionManager = new CollisionManager();
        this.dispatcher = dispatcher;
        this.coordinationPolicy = normalizeCoordinationPolicy(coordinationPolicy);
        this.runName = runName;
        this.tickMs = tickMs;
        this.maxTicks = maxTicks;
        this.seed = seed;
        this.workloadMode = workloadMode != null ? workloadMode : "FIXED_LIST";
        this.spawnRate = spawnRate;
        this.maxTasks = maxTasks;
        this.tasksGenerated = 0;
        this.ticksSinceLastSpawn = 0;
        this.initialized = map != null && robots != null && dispatcher != null;
    }

    /**
     * Constructs a new simulation engine instance based on the config file
     * @param configFilePath the path to the config JSON file
     */
    public SimulationEngine(String configFilePath) {
        if (configFilePath != null) {
            configInitialization(configFilePath);
        } else {
            // Default initialization if no file is provided
            this.tickCounter = 0;
            this.running = false;
            this.initialized = false;
            this.runId = UUID.randomUUID();
        }
    }

    /**
     * Initializes the simulation based on the JSON config file.
     * Does this by parsing the JSON file to the DTO classes, and from the DTO classes, we correctly set up the core simulation classes.
     * @param path the JSON config file path
     */
    private void configInitialization(String path) {
        try {
            this.initError = null;
            this.initialized = false;
            this.coordinationPolicy = CoordinationPolicy.noOp();

            // Load the DTO
            SimulationConfigDTO dto = ConfigLoader.load(path, SimulationConfigDTO.class);

            // initialize collision manager
            this.collisionManager = new CollisionManager();

            // Initialize the Map
            this.map = new Map(dto.map.mapId, dto.map.width, dto.map.height);

            // Update tile occupancy from the JSON
            if (dto.map.tiles != null) {
                for (SimulationConfigDTO.TileDTO tileDto : dto.map.tiles) {
                    Tile tile = this.map.getTile(tileDto.x, tileDto.y);
                    if (tile != null) {
                        tile.setOccupied(tileDto.isOccupied);
                    }
                }
            }

            // seed must be set before robot creation for strategy wiring
            this.seed = dto.config.seed;

            // Initialize Entities (Robots, Stations, Obstacles, etc.)
            // robots
            if (dto.entities != null && dto.entities.robots != null) {
                for (SimulationConfigDTO.RobotDTO rDto : dto.entities.robots) {
                    Vector2D pos = new Vector2D(rDto.position.x, rDto.position.y);
                    Robot robot = new Robot(rDto.id, rDto.name, pos);

                    robot.setBattery(rDto.battery);
                    robot.setStuckTicks(rDto.stuckTicks);
                    robot.setState(RobotState.valueOf(rDto.state));

                // normalize config strategy name and wire nav with seed
                AlgorithmType algo = AlgorithmType.fromConfigString(rDto.navigationStrategy);
                switch (algo) {
                    case GREEDY -> robot.setNav(new GreedyNavigationStrategy(this.seed));
                    case BUG -> robot.setNav(new BugNavigationStrategy(this.seed));
                    case RTA_STAR -> robot.setNav(new RtaStarNavigationStrategy(this.seed));
                    default -> {}
                }
                // do same with sensor strategy
                SensorType sensorType = SensorType.fromConfigString(rDto.sensorStrategy);
                switch (sensorType) {
                    case PROXIMITY -> robot.setSensor(new ProximitySensor());
                    case RANGE -> robot.setSensor(new RangeSensor());
                    default -> robot.setSensor(new ProximitySensor());
                }
                this.map.addEntity(robot);
            }  // closes for loop
        }  

            // Build RobotConfig from loaded config section
            this.robotConfig = new RobotConfig(
                dto.config.batteryCapacity     > 0 ? dto.config.batteryCapacity     : 100.0f,
                dto.config.lowBatteryThreshold > 0 ? dto.config.lowBatteryThreshold : 20.0f,
                dto.config.chargePerTick       > 0 ? dto.config.chargePerTick       : 5.0f,
                dto.config.energyPerMove       > 0 ? dto.config.energyPerMove       : 1.0f,
                dto.config.loadingTicks        > 0 ? dto.config.loadingTicks        : 1,
                dto.config.unloadingTicks      > 0 ? dto.config.unloadingTicks      : 1
            );
            for (MapEntity e : this.map.getEntities()) {
                if (e instanceof Robot r) r.setConfig(this.robotConfig);
            }

            // Save all entities from file into single array for simulation field
            this.robots = this.map.getEntities().stream().filter(e -> e instanceof Robot).map(e -> (Robot) e).toArray(Robot[]::new);

            // typed entity loading to preserve instanceof checks
            addStationsToMap(dto.entities.stations);
            addRacksToMap(dto.entities.racks);
            addObstaclesToMap(dto.entities.obstacles);

            // Initialize Dispatcher and Tasks
            this.dispatcher = new Dispatcher();
            if (dto.tasks != null) {
                // accumulate first so we can validate before adding to dispatcher
                List<Task> loadedTasks = new ArrayList<>();
                for (SimulationConfigDTO.TaskDTO tDto : dto.tasks) {
                    Task task = new Task(
                            tDto.id,
                            new Vector2D(tDto.pickupLocation.x, tDto.pickupLocation.y),
                            new Vector2D(tDto.dropoffLocation.x, tDto.dropoffLocation.y),
                            tDto.priority
                    );
                    task.setStatus(tDto.status);
                    loadedTasks.add(task);
                }
                // warn on config-loaded tasks whose pickup is a walled-in rack
                for (Task task : loadedTasks) {
                    if (this.map.isRackAt(task.getPickupLocation())
                            && !this.map.hasTraversableAdjacentTile(task.getPickupLocation())) {
                        System.err.println("warning: task " + task.getId()
                            + " pickup " + task.getPickupLocation()
                            + " is a walled-in rack — this task may never complete");
                    }
                    this.dispatcher.addTask(task);
                }
            }

            // Setup Coordination Policy
            if (dto.coordination != null) {
                if ("RESERVATION_K".equals(dto.coordination.type)) {
                    if (dto.coordination.k != null) {
                        this.coordinationPolicy = new ReservationKPolicy(dto.coordination.k);
                    }

                } else if ("TRAFFIC_RULES".equals(dto.coordination.type)) {
                    Set<Tile> intersectionTiles = new HashSet<>();
                    if (dto.coordination.intersections != null) {
                        for (SimulationConfigDTO.Vector2DDTO v : dto.coordination.intersections) {
                            Tile tile = this.map.getTile(v.x, v.y);
                            if (tile != null) {
                                intersectionTiles.add(tile);
                            }
                        }
                    }
                    this.coordinationPolicy = new TrafficRulesPolicy(intersectionTiles);
                }
            }

            // Set simulation state
            this.tickCounter = dto.simulation.tick;
            this.running = dto.simulation.isRunning;
            this.runId = dto.config.runId;
            this.runName = dto.config.runName;
            this.tickMs = dto.config.tickMs;
            this.maxTicks = dto.config.maxTicks;
            // seed already set before robot creation loop
            // this.speedMultiplier = dto.simulation.speedMultiplier; // not yet I believe

            // CollisionManager is always needed for tick()
            this.collisionManager = new CollisionManager();
            this.initialized = this.map != null && this.robots != null && this.dispatcher != null && this.collisionManager != null;

        } catch (Exception e) {
            this.initError = "Could not initialize simulation (" + e.getClass().getName() + "): " + e.getMessage();
            this.map = null;
            this.robots = null;
            this.dispatcher = null;
            this.coordinationPolicy = null;
            this.collisionManager = null;
            this.initialized = false;
            System.err.println("Error: " + initError);
            e.printStackTrace();
        }
    }

    // creates typed station entities, prefers dto type field over name heuristic
    private void addStationsToMap(List<SimulationConfigDTO.MapEntityDTO> entityDtos) {
        if (entityDtos == null) return;
        for (SimulationConfigDTO.MapEntityDTO eDto : entityDtos) {
            Vector2D pos = new Vector2D(eDto.position.x, eDto.position.y);
            // type field first, name fallback for old configs without type
            boolean isCharging = "CHARGING".equalsIgnoreCase(eDto.type)
                    || (eDto.type == null && eDto.name != null
                        && eDto.name.toLowerCase().contains("charg"));
            boolean isDelivery = "DELIVERY".equalsIgnoreCase(eDto.type)
                    || (eDto.type == null && eDto.name != null
                        && eDto.name.toLowerCase().contains("deliver"));
            if (isCharging) {
                this.map.addEntity(new ChargingStation(eDto.id, eDto.name, pos));
            } else if (isDelivery) {
                Tile tile = this.map.getTile(pos.getX(), pos.getY());
                if (tile != null) {
                    tile.setDeliveryStation(true);
                }
                this.map.addEntity(new DeliveryStation(eDto.id, eDto.name, pos));
            } else {
                this.map.addEntity(new MapEntity(eDto.id, eDto.name, pos)); // unknown type
            }
        }
    }

    // creates Rack entities to preserve type
    private void addRacksToMap(List<SimulationConfigDTO.RackDTO> entityDtos) {
        if (entityDtos == null) return;
        for (SimulationConfigDTO.RackDTO eDto : entityDtos) {
            Vector2D pos = new Vector2D(eDto.position.x, eDto.position.y);
            Rack rack = new Rack(eDto.id, eDto.name, pos);
            rack.setBoxCount(eDto.boxCount);
            if (eDto.validDropoffIds != null) {
                List<UUID> ids = new ArrayList<>();
                for (String s : eDto.validDropoffIds) {
                    try { ids.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
                }
                rack.setValidDropoffIds(ids);
            }
            this.map.addEntity(rack);
        }
    }

    // creates Obstacle entities so Map.isTraversable() works
    private void addObstaclesToMap(List<SimulationConfigDTO.MapEntityDTO> entityDtos) {
        if (entityDtos == null) return;
        for (SimulationConfigDTO.MapEntityDTO eDto : entityDtos) {
            Vector2D pos = new Vector2D(eDto.position.x, eDto.position.y);
            this.map.addEntity(new Obstacle(eDto.id, eDto.name, pos));
        }
    }

    /**
     * Saves the simulation state into a new JSON config file
     * Does this by retrieving core simulation classes current state (fields, metadata, etc) and saves it to the DTO classes which are easily translated back to JSON format.
     * @param path the new JSON config file path
     */
    public void configSaving(String path) throws IOException {
        if (map == null) {
            throw new IOException("map is not initialized");
        }
        SimulationConfigDTO dto = new SimulationConfigDTO();

        // Config Metadata
        dto.config = new SimulationConfigDTO.ConfigSection();
        dto.config.runId = this.runId;
        dto.config.runName = this.runName;
        dto.config.tickMs = this.tickMs;
        dto.config.maxTicks = this.maxTicks;
        dto.config.seed = this.seed;
        dto.config.batteryCapacity     = robotConfig.batteryCapacity;
        dto.config.lowBatteryThreshold = robotConfig.lowBatteryThreshold;
        dto.config.chargePerTick       = robotConfig.chargePerTick;
        dto.config.energyPerMove       = robotConfig.energyPerMove;
        dto.config.loadingTicks        = robotConfig.loadingTicks;
        dto.config.unloadingTicks      = robotConfig.unloadingTicks;

        // Map Section
        dto.map = new SimulationConfigDTO.MapSection();
        dto.map.mapId = map.getMapid();
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

        // instanceof classification so types round-trip through save/load
        for (MapEntity entity : map.getEntities()) {
            if (entity instanceof Robot) {
                dto.entities.robots.add(mapToRobotDTO((Robot) entity));
            } else if (entity instanceof ChargingStation) { // check before generic station
                SimulationConfigDTO.MapEntityDTO eDto = mapToEntityDTO(entity);
                eDto.type = "CHARGING"; // persisted so loader recreates correct type
                dto.entities.stations.add(eDto);
            } else if (entity instanceof DeliveryStation) {
                SimulationConfigDTO.MapEntityDTO eDto = mapToEntityDTO(entity);
                eDto.type = "DELIVERY"; // persisted so loader recreates correct type
                dto.entities.stations.add(eDto);
            } else if (entity instanceof Rack rack) {
                SimulationConfigDTO.RackDTO rDto = new SimulationConfigDTO.RackDTO();
                rDto.id = rack.getId();
                rDto.name = rack.getName();
                rDto.position = new SimulationConfigDTO.Vector2DDTO(
                    (int)rack.getPosition().getX(), (int)rack.getPosition().getY());
                rDto.boxCount = rack.getBoxCount();
                if (!rack.getValidDropoffIds().isEmpty()) {
                    rDto.validDropoffIds = rack.getValidDropoffIds().stream()
                        .map(UUID::toString).collect(java.util.stream.Collectors.toList());
                }
                dto.entities.racks.add(rDto);
            } else if (entity instanceof Obstacle) {
                dto.entities.obstacles.add(mapToEntityDTO(entity));
            } else {
                dto.entities.stations.add(mapToEntityDTO(entity)); // unknown type fallback
            }
        }

        // Tasks Section
        dto.tasks = new ArrayList<>();
        if (this.dispatcher != null) {
            for (Task task : this.dispatcher.getAllTasks()) {
                SimulationConfigDTO.TaskDTO tDto = new SimulationConfigDTO.TaskDTO();
                tDto.id = task.getId();
                tDto.pickupLocation = new SimulationConfigDTO.Vector2DDTO((int)task.getPickupLocation().getX(), (int)task.getPickupLocation().getY());
                tDto.dropoffLocation = new SimulationConfigDTO.Vector2DDTO((int)task.getDropoffLocation().getX(), (int)task.getDropoffLocation().getY());
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
        if (coordinationPolicy instanceof ReservationKPolicy) {
            dto.coordination = new SimulationConfigDTO.CoordinationSection();
            dto.coordination.type = "RESERVATION_K";
            dto.coordination.k = ((ReservationKPolicy) coordinationPolicy).getK();
        } else if (coordinationPolicy instanceof TrafficRulesPolicy) {
            dto.coordination = new SimulationConfigDTO.CoordinationSection();
            dto.coordination.type = "TRAFFIC_RULES";
            dto.coordination.intersections = new ArrayList<>();
            for (Tile tile : ((TrafficRulesPolicy) coordinationPolicy).getIntersectionTiles()) {
                if (tile != null) {
                    dto.coordination.intersections.add(new SimulationConfigDTO.Vector2DDTO(tile.getX(), tile.getY()));
                }
            }
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
        rDto.navigationStrategy = (robot.getNav() != null) ? robot.getNav().toString() : "NONE";
        rDto.sensorStrategy = (robot.getSensor() != null) ? robot.getSensor().toString() : "NONE";
        return rDto;
    }

    private String navigationStrategyKey(Robot robot) {
        if (robot == null || robot.getNav() == null) {
            return "NONE";
        }
        if (robot.getNav() instanceof GreedyNavigationStrategy) {
            return AlgorithmType.GREEDY.name();
        }
        return "NONE";
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

    /**
     * Runs a tick of the simulation
     *
     * <p>During each tick, the engine:
     * <ul>
     * <li>Collects movement intentions from all robots</li>
     * <li>Resolves conflicts and collisions using the {@link CollisionManager}</li>
     * <li>Commits the approved movements by updating the position state of each robot</li>
     * </ul>
     * </p>
     * @return true if the simulation is still running after this tick, false if the simulation has stopped (workload complete or was already stopped).
     */
    public boolean tick() {
        if (!initialized || robots == null || dispatcher == null || collisionManager == null || map == null) {
            throw new IllegalStateException("SimulationEngine not initialized correctly; cannot tick.");
        }

        if (tickCounter >= maxTicks) {
            this.running = false;
            return false;
        }

        // Checking if the warehouse workload has been completed.
        // "No configured tasks" is treated as sandbox mode: ticks still run.
        // spawnSourceExhausted bypasses the > 0 guard: if no pickups will ever exist, terminate.
        if (workloadComplete() && (dispatcher.getTotalTasksAdded() > 0 || spawnSourceExhausted)) {
            this.running = false;

            // Logging simulation run completion event
            SimulationRunRecordBuilder recordBuilder = new SimulationRunRecordBuilder(this);
            SimulationRunRecord record = recordBuilder.buildSimulationCompleteRecord();
            Logger.logSimulationRunEvent(SimulationRunEvent.RUN_COMPLETED, record);

            return false;
        }

        // Check if all robots have died
        if (allRobotsDead()) {
            this.running = false;
            return false;
        }

        // Any tick that executes work/sandbox progression is considered running.
        this.running = true;

        // SPAWN_RATE: dynamically generate tasks until maxTasks is reached
        if ("SPAWN_RATE".equals(workloadMode)) {
            spawnTasksIfNeeded();
        }

        // Assigning tasks to available robots
        dispatcher.assignTasks(robots);

        // Collecting initial move intentions from all robots
        MoveIntention[] intentions = collectIntentions();

        // Apply the selected coordination policy before collision resolution.
        MoveIntention[] coordinatedIntentions = coordinationPolicy.apply(map, intentions);

        // Resolving conflicts/collisions and finalizing move intentions for all robots
        MoveIntention[] finalMoveIntentions = collisionManager.resolveConflicts(coordinatedIntentions);

        // Commiting move intentions by updating all robot states
        updateRobotStates(finalMoveIntentions);

        // Track robot visits on tiles for heatmap
        trackVisits();

        // run per-robot state machine (charging, loading, unloading, energy)
        updateAllRobots();

        // Recovery runs after state updates
        recoverDeadlockedRobots();

        incrementTickCounter();

        // Re-evaluate completion after robot/task state transitions in this tick.
        if (workloadComplete()) {
            this.running = false;
        }

        return true;
    }

    /**
     * Checks if all robots in the simulation have reached a BATTERY_DEAD state
     * @return true if all robots are in the BATTER_DEAD state, false otherwise
     */
    private boolean allRobotsDead() {
        for (Robot robot : robots) {
            if (robot.getState() != RobotState.BATTER_DEAD) {
                return false;
            }
        }
        return true;
    }

    /**
     * Dynamically generates tasks on-the-fly in SPAWN_RATE mode.
     * Tasks spawn at a configurable rate (tasks-per-minute) until maxTasks is reached.
     */
    private void spawnTasksIfNeeded() { //TODO: either remove or ensure this works properly
        if (tasksGenerated >= maxTasks) return;
        if (spawnSourceExhausted) return; // no spawnable pickups — avoid repeated map scans
        if (map == null) return;

        // Rate: spawnRate tasks per minute.  tickMs controls sim speed (real-time ms per tick).
        // convert: ticks per minute = 60000 / tickMs
        // Tasks per spawn interval = spawnRate / (ticksPerMinute) = spawnRate * tickMs / 60000
        // spawn 1 task every (60000 / (spawnRate * tickMs)) ticks (rounded up).
        double ticksPerMinute = 60000.0 / Math.max(1, tickMs);
        int ticksPerSpawn = Math.max(1, (int) Math.round(ticksPerMinute / Math.max(1, spawnRate)));
        if (tickCounter == 0 || ticksSinceLastSpawn >= ticksPerSpawn) {
            spawnOneTask();
            ticksSinceLastSpawn = 0;
        } else {
            ticksSinceLastSpawn++;
        }
    }

    private int nextTaskId = 1;

    private void spawnOneTask() {
        if (tasksGenerated >= maxTasks) return;

        // collect reachable racks and delivery stations.
        // three-way branch to avoid falling back to floor-tile pickups when racks exist but are walled in.
        List<Rack> reachableRacks = new ArrayList<>();
        boolean anyRacksPresent = false;
        List<Vector2D> dropoffs = new ArrayList<>();
        for (MapEntity e : map.getEntities()) {
            if (e instanceof Rack r) {
                anyRacksPresent = true;
                if (map.hasTraversableAdjacentTile(r.getPosition())) {
                    reachableRacks.add(r);
                }
            } else if (e instanceof DeliveryStation) {
                dropoffs.add(e.getPosition());
            }
        }

        List<Vector2D> pickups;
        if (anyRacksPresent) {
            if (reachableRacks.isEmpty()) {
                // racks exist but all are walled in — mark exhausted so SPAWN_RATE terminates cleanly
                spawnSourceExhausted = true;
                return;
            }
            pickups = new ArrayList<>();
            for (Rack r : reachableRacks) pickups.add(r.getPosition());
        } else {
            // no racks on the map — legacy floor-tile fallback
            pickups = new ArrayList<>();
            for (int y = 0; y < map.getHeight(); y++) {
                for (int x = 0; x < map.getWidth(); x++) {
                    Vector2D p = new Vector2D(x, y);
                    // exclude all delivery station tiles, not just the first one
                    if (map.isTraversable(p) && !dropoffs.contains(p)) {
                        pickups.add(p);
                    }
                }
            }
        }
        // Fallback dropoff: any traversable tile not used as pickup
        if (dropoffs.isEmpty()) {
            for (int y = 0; y < map.getHeight(); y++) {
                for (int x = 0; x < map.getWidth(); x++) {
                    Vector2D p = new Vector2D(x, y);
                    if (map.isTraversable(p) && pickups.stream().noneMatch(pu -> pu.equals(p))) {
                        dropoffs.add(p);
                        break;
                    }
                }
                if (!dropoffs.isEmpty()) break;
            }
        }
        if (pickups.isEmpty() || dropoffs.isEmpty()) return;

        java.util.Random rng = new java.util.Random(seed + tickCounter + tasksGenerated);
        Vector2D pickup = pickups.get(rng.nextInt(pickups.size()));
        if (dropoffs.size() == 1 && dropoffs.get(0).equals(pickup)) {
            return;
        }
        Vector2D dropoff;
        do {
            dropoff = dropoffs.get(rng.nextInt(dropoffs.size()));
        } while (dropoff.equals(pickup) && dropoffs.size() > 1);

        int priority = 1;
        Task task = new Task(nextTaskId++, pickup, dropoff, priority);
        dispatcher.addTask(task);
        tasksGenerated++;
    }

    /**
     * Indicates if the warehouse workload has been completed.
     * In SPAWN_RATE mode, also checks that all tasks have been generated.
     * @return true if there are no pending tasks AND all robots are idle.
     */
    private boolean workloadComplete() {
        if ("SPAWN_RATE".equals(workloadMode)) {
            // In SPAWN_RATE mode, complete when:
            // - no pending tasks in dispatcher AND
            // - all robots are idle (nothing in flight) AND
            // - we have generated maxTasks (no more to come)
            if (dispatcher.hasPendingTasks()) return false;
            for (Robot robot : robots) {
                if (robot.getCurrentTask() != null || robot.getState() != RobotState.IDLE) {
                    return false;
                }
            }
            // spawnSourceExhausted: racks exist but none reachable — treat as workload exhausted
            return tasksGenerated >= maxTasks || spawnSourceExhausted;
        } else {
            // FIXED_LIST mode: complete when dispatcher is empty and all robots idle
            if (dispatcher.hasPendingTasks()) return false;
            for (Robot robot : robots) {
                if (robot.getCurrentTask() != null || robot.getState() != RobotState.IDLE) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Collects move intentions for all robots in the simulation
     * @return an array of MoveIntentions, one for each robot in the simulation
     */
    private MoveIntention[] collectIntentions() {
        MoveIntention[] intentions = new MoveIntention[robots.length];

        // Collection MoveIntentions for each robot
        for (int i = 0; i < robots.length; i++) {
            intentions[i] = robots[i].getNextMove(map);
        }

        return intentions;
    }

    /**
     * Updates states for all robots based on commited move intentions
     * @param intentions an array of finalized MoveIntentions that are ready to be commited for every robot
     */
    private void updateRobotStates(MoveIntention[] intentions) {
        // Move robots to their "to" tile when present.
        for (MoveIntention intention : intentions) {
            if (intention == null || intention.getRobot() == null || intention.getToTile() == null) {
                continue;
            }
            Robot robot = intention.getRobot();
            Vector2D newPosition = intention.getToTile().getPosition();
            robot.setPosition(newPosition);
        }
    }

    // calls update() on every robot for state machine transitions
    private void updateAllRobots() {
        for (Robot robot : robots) {
            robot.update(this.map);
        }
    }

    private void recoverDeadlockedRobots() {
        for (Robot robot : robots) {
            // Only recover robots that are still actively working on a task and have exceeded the threshold.
            if (robot == null || robot.getState() != RobotState.MOVING || robot.getCurrentTask() == null) {
                continue;
            }

            // Skipping recovery for robots with depleted batteries
            // TODO: Consider a method for handling dead robots
            if (robot.getState() == RobotState.BATTER_DEAD) {
                continue; // Let battery recovery handle this robot, don't interfere with task recovery
            }

            if (robot.getStuckTicks() < DEADLOCK_RECOVERY_THRESHOLD) {
                continue;
            }

            // Logging robot deadlock detection event
            try {
                // Serialize the number of stuck ticks of the robot
                ObjectMapper mapper = new ObjectMapper();
                java.util.Map<String, Integer> data = new HashMap<>();
                data.put("stuckTicks", robot.getStuckTicks());
                String json = mapper.writeValueAsString(data);

                SimLogRecordBuilder recordBuilder = new SimLogRecordBuilder(this.runId, this.tickCounter, robot.getId(), robot.getPosition().getX(), robot.getPosition().getY());
                SimLogRecord deadlockRecord = recordBuilder.buildDeadlockDetectionRecord(json);
                Logger.logRobotEvent(RobotEvent.DEADLOCK_DETECTED, deadlockRecord);
            } catch (JsonProcessingException e) {
                System.err.println("Error serializing deadlock data while logging deadlock detection event: " + e.getMessage());
            }

            Task task = robot.getCurrentTask();
            // Stage 1: try one local reroute before dropping the task.
            if (!robot.hasRerouteAttemptedForCurrentTask() && robot.canStartDeadlockRerouteAttempt()) {
                coordinationPolicy.clearRobotCoordinationState(robot);
                if (robot.startDeadlockRerouteAttempt()) {
                    continue;
                }
            }

            // Stage 2: if rerouting is exhausted or impossible, fall back to reset-and-requeue.
            if (task != null) {
                dispatcher.requeueTask(task);

                // Logging robot deadlock recovery event via task requeue
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    java.util.Map<String, String> data = new HashMap<>();
                    data.put("resolutionMethod", "task_requeue");
                    String json = mapper.writeValueAsString(data);

                    SimLogRecordBuilder recordBuilder = new SimLogRecordBuilder(this.runId, this.tickCounter, robot.getId(), robot.getPosition().getX(), robot.getPosition().getY());
                    SimLogRecord recoveryRecord = recordBuilder.buildDeadlockResolutionRecord(json);
                    Logger.logRobotEvent(RobotEvent.DEADLOCK_RESOLVED, recoveryRecord);
                } catch (JsonProcessingException e) {
                    System.err.println("Error serializing recovery data while logging deadlock recovery event: " + e.getMessage());
                }
            }

            // Policies could hold per-robot coordination state that should be released on fallback recovery.
            coordinationPolicy.clearRobotCoordinationState(robot);
            robot.recoverFromDeadlock();
        }
    }

    /**
     * Increments the tick counter for the simulation engine
     */
    private void incrementTickCounter() {
        tickCounter++;
    }

    /**
     * Tracks robot visits on tiles for heatmap visualization.
     * Only robots that are actively moving contribute to visit counts —
     * idle, charging, loading, or unloading robots are excluded so they
     * do not inflate the count on a single tile.
     */
    private void trackVisits() {
        if (robots == null || map == null) return;
        for (Robot robot : robots) {
            // Only count visits when the robot is actively navigating;
            // idle/charging/loading/unloading states would inflate a single tile.
            if (robot.getState() != RobotState.MOVING) continue;
            Tile tile = map.getTile(robot.getPosition().getX(), robot.getPosition().getY());
            if (tile != null) {
                tile.incrementVisitCount();
            }
        }
    }

    private CoordinationPolicy normalizeCoordinationPolicy(CoordinationPolicy coordinationPolicy) {
        return coordinationPolicy != null ? coordinationPolicy : CoordinationPolicy.noOp();
    }

    public RobotConfig getRobotConfig() { return robotConfig; }

    /** Replaces the robot physics config and propagates it to all loaded robots. */
    public void setRobotConfig(RobotConfig config) {
        this.robotConfig = config;
        if (robots != null) {
            for (Robot r : robots) r.setConfig(config);
        }
    }

    // Getters
    public UUID getRunId() {
        return runId;
    }

    public Map getMap() {
        return map;
    }

    public int getTickCounter() {
        return tickCounter;
    }

    public int getMaxTicks() {
        return maxTicks;
    }

    public int getTickMs() {
        return tickMs;
    }

    public boolean getIsRunning() {
        return running;
    }

    public Robot[] getRobots() {
        return robots;
    }

    public long getSeed() { return seed; }

    /**
     * Gets the name of the coordination policy class being used in this simulation.
     * @return the name of the coordination policy class, or null if no policy is set
     */
    public String getCoordinationPolicy() {
        return coordinationPolicy != null ? coordinationPolicy.getClass().getName() : null;
    }

    /**
     * Returns the dispatcher for task management.
     * @return the dispatcher
     */
    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * Returns the initialization error message, if any.
     * @return the initialization error message or null if no error occurred.
     */
    public String getInitError() {
        return initError;
    }

    /**
     * Updates the runId with a new random UUID. This is used when restarting a simulation.
     */
    public void updateRunId() {
        this.runId = UUID.randomUUID();
    }

    /**
     * Adds an entity to the simulation map at runtime.
     * @param entity the entity to add
     */
    public void addEntity(MapEntity entity) {
        if (map != null && entity != null) {
            map.addEntity(entity);
            if (entity instanceof Robot) {
                refreshRobotsArray();
            }
        }
    }

    /**
     * Removes an entity from the simulation map at runtime.
     * @param entity the entity to remove
     * @return true if the entity was removed
     */
    public boolean removeEntity(MapEntity entity) {
        if (map != null && entity != null) {
            boolean removed = map.removeEntity(entity);
            if (removed && entity instanceof Robot) {
                refreshRobotsArray();
            }
            return removed;
        }
        return false;
    }

    private void refreshRobotsArray() {
        if (map != null) {
            this.robots = map.getEntities().stream()
                    .filter(e -> e instanceof Robot)
                    .map(e -> (Robot) e)
                    .toArray(Robot[]::new);
        }
    }
}
