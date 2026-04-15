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
import com.openrobotics.robot.navigation.NavigationStrategy;
import com.openrobotics.robot.sensors.SensorStrategy;
import com.openrobotics.task.*;
import java.io.IOException;
import java.util.*;

/** Tick-based simulation engine; coordinates robot movement, task dispatch, collision resolution, and deadlock recovery. */
public class SimulationEngine {
    private UUID runId;
    private static final int DEADLOCK_RECOVERY_THRESHOLD = 5;
    private int tickCounter;
    private boolean running;
    private Map map;
    private Robot[] robots;
    private CollisionManager collisionManager;
    private Dispatcher dispatcher;
    private CoordinationPolicy coordinationPolicy;
    private SimulationError simulationError;

    private RobotConfig robotConfig = RobotConfig.defaults();

    private String runName;
    private int tickMs;
    private int maxTicks;
    private int maxTasks;
    private long seed;
    private boolean manualTaskAssignment = false;
    private boolean initialized;
    private String initError;

    /**
     * Creates an engine with default run name, {@code tickMs=100}, {@code maxTicks=5000}, {@code seed=42}.
     *
     * @param map the warehouse map
     * @param robots the array of robots to simulate
     * @param dispatcher the task dispatcher
     * @param coordinationPolicy the coordination policy; {@code null} is treated as no-op
     */
    public SimulationEngine(Map map, Robot[] robots, Dispatcher dispatcher, CoordinationPolicy coordinationPolicy) {
        this(map, robots, dispatcher, coordinationPolicy, "default_run", 100, 5000, 42);
    }

    /**
     * Creates an engine with an explicit run config; delegates to the 9-param constructor with {@code maxTasks=10}.
     *
     * @param map the warehouse map
     * @param robots the array of robots to simulate
     * @param dispatcher the task dispatcher
     * @param coordinationPolicy the coordination policy; {@code null} is treated as no-op
     * @param runName name used to identify this run in logs and database records
     * @param tickMs milliseconds per simulation tick
     * @param maxTicks maximum number of ticks before the simulation halts
     * @param seed RNG seed for reproducible task generation and navigation
     */
    public SimulationEngine(Map map, Robot[] robots, Dispatcher dispatcher, CoordinationPolicy coordinationPolicy,
                          String runName, int tickMs, int maxTicks, long seed) {
        this(map, robots, dispatcher, coordinationPolicy, runName, tickMs, maxTicks, seed, 10);
    }

    /**
     * Primary constructor; all other programmatic constructors delegate here.
     *
     * @param map the warehouse map
     * @param robots the array of robots to simulate
     * @param dispatcher the task dispatcher
     * @param coordinationPolicy the coordination policy; {@code null} is treated as no-op
     * @param runName name used to identify this run in logs and database records
     * @param tickMs milliseconds per simulation tick
     * @param maxTicks maximum number of ticks before the simulation halts
     * @param seed RNG seed for reproducible task generation and navigation
     * @param maxTasks absolute cap on total tasks generated (applies to both static and dynamic workload modes)
     */
    public SimulationEngine(Map map, Robot[] robots, Dispatcher dispatcher, CoordinationPolicy coordinationPolicy,
                          String runName, int tickMs, int maxTicks, long seed, int maxTasks) {

        this.runId = UUID.randomUUID();
        this.simulationError = SimulationError.NONE;
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
        this.maxTasks = maxTasks;
        this.seed = seed;
        this.initialized = map != null && robots != null && dispatcher != null;
    }

    /**
     * Creates an engine by loading state from a JSON config file.
     * Use {@link #getInitError()} to check whether loading succeeded.
     *
     * @param configFilePath path to the JSON config file; {@code null} produces an uninitialised engine
     */
    public SimulationEngine(String configFilePath) {
        if (configFilePath != null) {
            configInitialization(configFilePath);
        } else {
            this.tickCounter = 0;
            this.running = false;
            this.initialized = false;
            this.runId = UUID.randomUUID();
        }
    }

    // parses the JSON config file into DTOs and wires up all simulation state from them
    private void configInitialization(String path) {
        try {
            this.initError = null;
            this.initialized = false;
            this.coordinationPolicy = CoordinationPolicy.noOp();

            SimulationConfigDTO dto = ConfigLoader.load(path, SimulationConfigDTO.class);

            this.collisionManager = new CollisionManager();
            this.map = new Map(dto.map.mapId, dto.map.width, dto.map.height);

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

            if (dto.entities != null && dto.entities.robots != null) {
                for (SimulationConfigDTO.RobotDTO rDto : dto.entities.robots) {
                    Vector2D pos = new Vector2D(rDto.position.x, rDto.position.y);
                    Robot robot = new Robot(rDto.id, rDto.name, pos);

                    robot.setBattery(rDto.battery);
                    robot.setStuckTicks(rDto.stuckTicks);
                    robot.setState(RobotState.valueOf(rDto.state));

                    AlgorithmType algo = AlgorithmType.fromConfigString(rDto.navigationStrategy);
                    robot.setNav(NavigationStrategy.create(algo, this.seed));
                    SensorType sensorType = SensorType.fromConfigString(rDto.sensorStrategy);
                    robot.setSensor(SensorStrategy.create(sensorType));
                    this.map.addEntity(robot);
                }
            }

            this.robotConfig = new RobotConfig(
                dto.config.batteryCapacity > 0 ? dto.config.batteryCapacity : 100.0f,
                dto.config.lowBatteryThreshold > 0 ? dto.config.lowBatteryThreshold : 20.0f,
                dto.config.chargePerTick > 0 ? dto.config.chargePerTick : 5.0f,
                dto.config.energyPerMove > 0 ? dto.config.energyPerMove : 1.0f,
                dto.config.loadingTicks > 0 ? dto.config.loadingTicks : 1,
                dto.config.unloadingTicks > 0 ? dto.config.unloadingTicks : 1
            );
            for (MapEntity e : this.map.getEntities()) {
                if (e instanceof Robot r) r.setConfig(this.robotConfig);
            }

            this.robots = this.map.getEntities().stream().filter(e -> e instanceof Robot).map(e -> (Robot) e).toArray(Robot[]::new);

            // typed entity loading to preserve instanceof checks
            addStationsToMap(dto.entities.stations);
            addRacksToMap(dto.entities.racks);
            addObstaclesToMap(dto.entities.obstacles);

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

            this.tickCounter = dto.simulation.tick;
            this.running = dto.simulation.isRunning;
            this.runId = dto.config.runId;
            this.runName = dto.config.runName;
            this.tickMs = dto.config.tickMs;
            this.maxTicks = dto.config.maxTicks;
            this.maxTasks = dto.config.maxTasks > 0 ? dto.config.maxTasks : 10;
            this.manualTaskAssignment = dto.config.manualTaskAssignment;
            this.simulationError = SimulationError.NONE;
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
            rack.setManualDropoffAssignment(eDto.manualDropoffAssignment);
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
     * Saves the current simulation state to a JSON config file.
     *
     * @param path destination file path
     * @throws IOException if the map is not initialized or the file cannot be written
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
        dto.config.maxTasks = this.maxTasks;
        dto.config.manualTaskAssignment = this.manualTaskAssignment;
        dto.config.batteryCapacity = robotConfig.batteryCapacity;
        dto.config.lowBatteryThreshold = robotConfig.lowBatteryThreshold;
        dto.config.chargePerTick = robotConfig.chargePerTick;
        dto.config.energyPerMove = robotConfig.energyPerMove;
        dto.config.loadingTicks = robotConfig.loadingTicks;
        dto.config.unloadingTicks = robotConfig.unloadingTicks;

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
                rDto.manualDropoffAssignment = rack.isManualDropoffAssignment();
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
            for (Task task : this.dispatcher.getAllQueuedTasks()) {
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


    // maps a Robot to its DTO for config serialization
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

    // maps a MapEntity to its DTO for config serialization
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
     * Advances the simulation by one tick: dispatches tasks, collects intentions, applies coordination policy,
     * resolves conflicts, commits moves, runs robot state machines, and handles deadlock recovery.
     *
     * @return {@code true} if the simulation is still running; {@code false} when the workload is complete,
     *         the tick limit is reached, or all robots are dead
     */
    public boolean tick() {
        if (!initialized || robots == null || dispatcher == null || collisionManager == null || map == null) {
            throw new IllegalStateException("SimulationEngine not initialized correctly; cannot tick.");
        }

        // guard against ticking when no robots are present to prevent infinite loops in sim engine
        if (robots.length == 0) {
            this.running = false;
            simulationError = SimulationError.NO_ROBOTS_SPAWNED;
            return false;
        }

        if (tickCounter >= maxTicks) {
            this.running = false;
            return false;
        }

        // "No configured tasks" is treated as sandbox mode: ticks still run.
        // Only short-circuit when a workload was actually configured and is now finished.
        if (dispatcher.getTotalTasksAdded() > 0 && workloadComplete()) {
            this.running = false;

            SimulationRunRecordBuilder recordBuilder = new SimulationRunRecordBuilder(this);
            SimulationRunRecord record = recordBuilder.buildSimulationCompleteRecord();
            Logger.logSimulationRunEvent(SimulationRunEvent.RUN_COMPLETED, record);

            return false;
        }

        if (allRobotsDead()) {
            this.running = false;
            simulationError = SimulationError.ALL_ROBOTS_DEAD;
            return false;
        }

        // Any tick that executes work/sandbox progression is considered running.
        this.running = true;

        // Assigning tasks to available robots
        dispatcher.assignTasks(robots);

        // Collecting initial move intentions from all robots
        MoveIntention[] intentions = collectIntentions();

        // Apply the selected coordination policy before collision resolution.
        MoveIntention[] coordinatedIntentions = coordinationPolicy.apply(map, intentions);

        // Resolving conflicts/collisions and finalizing move intentions for all robots
        MoveIntention[] finalMoveIntentions = collisionManager.resolveConflicts(map, coordinatedIntentions);

        // committing move intentions by updating all robot states
        updateRobotStates(finalMoveIntentions);

        // Track robot visits on tiles for heatmap
        trackVisits();

        // run per-robot state machine (charging, loading, unloading, energy)
        updateAllRobots();

        // Checks if any robots are dead and requeues their assigned task if so
        requeueDeadRobotsTasks();

        // Recovery runs after state updates
        recoverDeadlockedRobots();

        incrementTickCounter();

        // Re-evaluate completion after robot/task state transitions in this tick.
        if (workloadComplete()) {
            this.running = false;
        }

        if (allRobotsDead()) {
            this.running = false;
            simulationError = SimulationError.ALL_ROBOTS_DEAD;
            return false;
        }

        return true;
    }

    // returns true when every robot is in BATTERY_DEAD state; false if no robots are loaded
    private boolean allRobotsDead() {
        if (robots.length == 0) return false; // no robots were loaded in the sim engine

        for (Robot robot : robots) {
            if (robot.getState() != RobotState.BATTERY_DEAD) {
                return false;
            }
        }
        return true;
    }

    // returns true when all tasks in the lifetime list have COMPLETED status
    private boolean workloadComplete() {
        // Checking to see if tasks were ever added to the dispatcher
        if (dispatcher.getLifetimeTasks().isEmpty()) {
                return true; // no tasks were ever added to the dispatcher
        }

        for (Task task : dispatcher.getLifetimeTasks()) {
            if (task.getStatus() != TaskStatus.COMPLETED) {
                return false; // found a task that is not completed, so workload is not complete
            }
        }

        return true; // all tasks are completed
    }

    // collects one MoveIntention per robot for this tick
    private MoveIntention[] collectIntentions() {
        MoveIntention[] intentions = new MoveIntention[robots.length];

        for (int i = 0; i < robots.length; i++) {
            intentions[i] = robots[i].getNextMove(map);
        }

        return intentions;
    }

    // requeues the task of any robot that just died so it can be reassigned
    private void requeueDeadRobotsTasks() {
        for (Robot robot : robots) {
            if (robot.getState() == RobotState.BATTERY_DEAD && robot.getCurrentTask() != null) {
                dispatcher.requeueTask(robot.getCurrentTask());
                robot.setCurrentTask(null);
            }
        }
    }

    // applies approved move intentions by updating each robot's position
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
            if (robot.getState() == RobotState.BATTERY_DEAD) {
                continue; // Let battery recovery handle this robot, don't interfere with task recovery
            }

            if (robot.getStuckTicks() < DEADLOCK_RECOVERY_THRESHOLD) {
                continue;
            }

            try {
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

    private void incrementTickCounter() {
        tickCounter++;
    }

    // increments visit counts only for MOVING robots; other states would inflate the count on a single tile
    private void trackVisits() {
        if (robots == null || map == null) return;
        for (Robot robot : robots) {
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

    private TrafficRulesPolicy buildTrafficRulesPolicy(Set<Vector2D> intersectionPositions) {
        Set<Tile> tiles = new HashSet<>();
        if (map != null && intersectionPositions != null) {
            for (Vector2D position : intersectionPositions) {
                if (position == null) continue;
                Tile tile = map.getTile(position.getX(), position.getY());
                if (tile != null) {
                    tiles.add(tile);
                }
            }
        }
        return new TrafficRulesPolicy(tiles);
    }

    public RobotConfig getRobotConfig() { return robotConfig; }

    /**
     * Replaces the robot physics config and propagates it to all loaded robots.
     *
     * @param config the new robot config to apply
     */
    public void setRobotConfig(RobotConfig config) {
        this.robotConfig = config;
        if (robots != null) {
            for (Robot r : robots) r.setConfig(config);
        }
    }

    public UUID getRunId() {
        return runId;
    }

    public Map getMap() {
        return map;
    }

    public int getTickCounter() {
        return tickCounter;
    }

    public int getMaxTasks() { return maxTasks; }

    public boolean isManualTaskAssignment() { return manualTaskAssignment; }
    public void setManualTaskAssignment(boolean manualTaskAssignment) {
        this.manualTaskAssignment = manualTaskAssignment;
    }

    /**
     * Returns {@code true} when the clock has started, every robot is idle, and the dispatcher has no
     * more queued tasks. A {@code tickCounter} of {@code 0} is never considered finished, so Play on a
     * fresh engine is always allowed.
     *
     * @return {@code true} if the run is finished; {@code false} otherwise
     */
    public boolean isFinished() {
        if (tickCounter <= 0) return false;
        if (dispatcher == null || !dispatcher.getAllQueuedTasks().isEmpty()) return false;
        if (robots == null) return true;
        for (Robot r : robots) {
            if (r == null) continue;
            if (r.getState() != RobotState.IDLE) return false;
        }
        return true;
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
     * Returns the fully-qualified class name of the active coordination policy.
     *
     * @return the class name of the active policy, or {@code null} if none is set
     */
    public String getCoordinationPolicy() {
        return coordinationPolicy != null ? coordinationPolicy.getClass().getName() : null;
    }

    /**
     * Returns whether the active coordination policy is traffic-rules based.
     *
     * @return {@code true} if the active policy is a {@link TrafficRulesPolicy}
     */
    public boolean usesTrafficRulesPolicy() {
        return coordinationPolicy instanceof TrafficRulesPolicy;
    }

    /**
     * Returns a copy of the configured traffic-rules intersection positions.
     *
     * @return unmodifiable set of intersection positions; empty if the active policy is not traffic-rules based
     */
    public Set<Vector2D> getTrafficRuleIntersections() {
        if (!(coordinationPolicy instanceof TrafficRulesPolicy policy)) {
            return Collections.emptySet();
        }

        Set<Vector2D> intersections = new HashSet<>();
        for (Tile tile : policy.getIntersectionTiles()) {
            if (tile != null) {
                intersections.add(new Vector2D(tile.getX(), tile.getY()));
            }
        }
        return Collections.unmodifiableSet(intersections);
    }

    /**
     * Returns whether the provided tile is configured as a traffic-rules intersection.
     *
     * @param x the tile x-coordinate
     * @param y the tile y-coordinate
     * @return {@code true} if the tile at ({@code x}, {@code y}) is a registered intersection
     */
    public boolean hasTrafficRuleIntersection(int x, int y) {
        return getTrafficRuleIntersections().contains(new Vector2D(x, y));
    }

    /**
     * Toggles the provided tile in the traffic-rules intersection set.
     *
     * @param x the tile x-coordinate to toggle
     * @param y the tile y-coordinate to toggle
     * @return {@code true} when the active policy supports traffic-rule intersections
     *         and the coordinate exists on the map; {@code false} otherwise
     */
    public boolean toggleTrafficRuleIntersection(int x, int y) {
        if (!usesTrafficRulesPolicy() || map == null || map.getTile(x, y) == null) {
            return false;
        }

        Set<Vector2D> intersections = new HashSet<>(getTrafficRuleIntersections());
        Vector2D target = new Vector2D(x, y);
        if (!intersections.add(target)) {
            intersections.remove(target);
        }

        coordinationPolicy = buildTrafficRulesPolicy(intersections);
        return true;
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * Returns the initialization error message set during config loading.
     *
     * @return the error message, or {@code null} if config loading succeeded
     */
    public String getInitError() {
        return initError;
    }

    public SimulationError getSimulationError() {
        return simulationError;
    }

    /**
     * Returns a user-friendly error message based on the current simulation error state.
     *
     * @return a user-friendly error message if a simulation error is present, or {@code null} if no error has occurred
     */
    public String getSimulationErrorMessage() {
        if (simulationError == null) {
            System.out.println("[Error] Simulation error state should not be null.");
            return "An unknown error has occurred in the simulation.";
        }

        return simulationError.getMessage();
    }

    /**
     * Sets the current simulation error state
     * @param error the SimulationError to set for the simulation
     */
    public void setSimulationError(SimulationError error) {
        this.simulationError = error;
    }

    // generates a new runId; called on reset so logging for each run is isolated
    private void updateRunId() {
        this.runId = UUID.randomUUID();
    }

    /**
     * Adds an entity to the simulation map at runtime.
     *
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
     *
     * @param entity the entity to remove
     * @return {@code true} if the entity was present and removed; {@code false} otherwise
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
     * Resets the simulation engine by updating the runId and clearing any simulation errors.
     */
    public void reset() {
        updateRunId();
        this.simulationError = SimulationError.NONE;
    }
}
