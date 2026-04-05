package com.openrobotics.simulationcore;

import com.openrobotics.io.ConfigLoader;
import com.openrobotics.io.SimulationConfigDTO;
import com.openrobotics.map.*;
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

/**
 * The SimulationEngine is the core component responsible for advancing the
 * simulation. It coordinates the progression of discrete simulation steps
 * ("ticks") and manages interactions between robots within the environment.
 * The engine maintains an internal tick counter that represents the number
 * of simulation steps that have been executed.
 */
public class SimulationEngine {
    private static final int DEADLOCK_RECOVERY_THRESHOLD = 5;
    private int tickCounter;
    private boolean running; // tracks if the simulation is still running
    private Map map;
    private Robot[] robots;
    private CollisionManager collisionManager;
    private Dispatcher dispatcher;
    private CoordinationPolicy coordinationPolicy;

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

            // Save all entities from file into single array for simulation field
            this.robots = this.map.getEntities().stream().filter(e -> e instanceof Robot).map(e -> (Robot) e).toArray(Robot[]::new);

            // typed entity loading to preserve instanceof checks
            addStationsToMap(dto.entities.stations);
            addRacksToMap(dto.entities.racks);
            addObstaclesToMap(dto.entities.obstacles);

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
                    if (dto.coordination.k != null) {
                        this.coordinationPolicy = new ReservationKPolicy(dto.coordination.k);
                    }

                } else if ("TRAFFIC_RULES".equals(dto.coordination.type)) {
                    Set<Tile> intersectionTiles = new HashSet<>();
                    if (dto.coordination.intersections != null) {
                        for (SimulationConfigDTO.Vector2DDTO v : dto.coordination.intersections) {
                            intersectionTiles.add(this.map.getTile(v.x, v.y));
                        }
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
    private void addRacksToMap(List<SimulationConfigDTO.MapEntityDTO> entityDtos) {
        if (entityDtos == null) return;
        for (SimulationConfigDTO.MapEntityDTO eDto : entityDtos) {
            Vector2D pos = new Vector2D(eDto.position.x, eDto.position.y);
            this.map.addEntity(new Rack(eDto.id, eDto.name, pos));
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
            } else if (entity instanceof Rack) {
                dto.entities.racks.add(mapToEntityDTO(entity));
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

    public Map getMap() {
        return map;
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
        if (workloadComplete()) {
            this.running = false;
            return false;
        }

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
        return true;
    }

    /**
     * Dynamically generates tasks on-the-fly in SPAWN_RATE mode.
     * Tasks spawn at a configurable rate (tasks-per-minute) until maxTasks is reached.
     */
    private void spawnTasksIfNeeded() {
        if (tasksGenerated >= maxTasks) return;
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

        // Collect all racks (pickup) and delivery stations (dropoff)
        List<Vector2D> pickups = new ArrayList<>();
        List<Vector2D> dropoffs = new ArrayList<>();
        for (MapEntity e : map.getEntities()) {
            if (e instanceof Rack) {
                pickups.add(e.getPosition());
            } else if (e instanceof DeliveryStation) {
                dropoffs.add(e.getPosition());
            }
        }
        // Fallback: use any traversable non-obstacle tile as pickup if no racks exist
        if (pickups.isEmpty()) {
            for (int y = 0; y < map.getHeight(); y++) {
                for (int x = 0; x < map.getWidth(); x++) {
                    Vector2D p = new Vector2D(x, y);
                    if (map.isTraversable(p) && !p.equals(dropoffs.isEmpty() ? null : dropoffs.get(0))) {
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
            return tasksGenerated >= maxTasks;
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
            robot.update();
        }
    }

    private void recoverDeadlockedRobots() {
        for (Robot robot : robots) {
            // Only recover robots that are still actively working on a task and have exceeded the threshold.
            if (robot == null || robot.getState() != RobotState.MOVING || robot.getCurrentTask() == null) {
                continue;
            }
            if (robot.getStuckTicks() < DEADLOCK_RECOVERY_THRESHOLD) {
                continue;
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
     * Each robot's current position increments the visit count of that tile.
     */
    private void trackVisits() {
        if (robots == null || map == null) return;
        for (Robot robot : robots) {
            Tile tile = map.getTile(robot.getPosition().getX(), robot.getPosition().getY());
            if (tile != null) {
                tile.incrementVisitCount();
            }
        }
    }

    private CoordinationPolicy normalizeCoordinationPolicy(CoordinationPolicy coordinationPolicy) {
        return coordinationPolicy != null ? coordinationPolicy : CoordinationPolicy.noOp();
    }

    // Getters
    public int getTickCounter() {
        return tickCounter;
    }

    public boolean getIsRunning() {
        return running;
    }

    public Robot[] getRobots() {
        return robots;
    }

    /**
     * Returns the initialization error message, if any.
     * @return the initialization error message or null if no error occurred.
     */
    public String getInitError() {
        return initError;
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

    /**
     * Returns the dispatcher for task management.
     * @return the dispatcher
     */
    public Dispatcher getDispatcher() {
        return dispatcher;
    }
}
