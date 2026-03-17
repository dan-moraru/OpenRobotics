package com.openrobotics.simulationcore;

import com.openrobotics.io.ConfigLoader;
import com.openrobotics.io.SimulationConfigDTO;
import com.openrobotics.map.*;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.robot.*;
import com.openrobotics.task.*;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * The SimulationEngine is the core component responsible for advancing the
 * simulation. It coordinates the progression of discrete simulation steps
 * ("ticks") and manages interactions between robots within the environment.
 * The engine maintains an internal tick counter that represents the number
 * of simulation steps that have been executed.
 */
public class SimulationEngine {
    private record ReservationKey(int timeStep, Vector2D position) {} // One slot in res table.
    // Represents one robots full res attempt for the current tick.
    private record ReservationRequest(UUID robotId, MoveIntention move, List<ReservationKey> window) {}

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

    // Central Reservation_K state owned by the engine.
    // The outer key is the absolute time step and the inner map records which robot
    // owns a tile at that step.
    private final java.util.Map<Integer, java.util.Map<Vector2D, UUID>> reservationTable = new HashMap<>();
    // Secondary index so all outstanding reservations for a robot can be cleared quickly.
    private final java.util.Map<UUID, Set<ReservationKey>> reservationsByRobot = new HashMap<>();

    /**
     * Constructs a new simulation engine instance
     * @param map the map representing the warehouse environment
     * @param robots the robots
     * @param dispatcher the task dispatcher loaded with tasks ready to be dispatched to robots
     * @param coordinationPolicy the set coordination policy between robots that is followed when moving around the map
     */
    public SimulationEngine(Map map, Robot[] robots, Dispatcher dispatcher, CoordinationPolicy coordinationPolicy) {
        this.tickCounter = 0;
        this.map = map;
        this.robots = robots;
        this.collisionManager = new CollisionManager();
        this.dispatcher = dispatcher;
        // Default to a coordination policy if none is configured
        this.coordinationPolicy = (coordinationPolicy != null)
                ? coordinationPolicy
                : CoordinationPolicy.noOp();
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
        }
    }

    /**
     * Initializes the simulation based on the JSON config file.
     * Does this by parsing the JSON file to the DTO classes, and from the DTO classes, we correctly set up the core simulation classes.
     * @param path the JSON config file path
     */
    private void configInitialization(String path) {
        try {
            this.collisionManager = new CollisionManager();
            // Start from default policy so configs without a coordination section still run.
            this.coordinationPolicy = CoordinationPolicy.noOp();

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

            // seed must be set before robot creation for strategy wiring
            this.seed = dto.config.seed;

            // Initialize Entities (Robots, Stations, Obstacles, etc.)
            // robots
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
                    // BUG, RTA_STAR: future tasks
                    default -> {} // nav stays null, getNextMove handles it safely
                }

                this.map.addEntity(robot);
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
                    this.coordinationPolicy = new ReservationKPolicy(dto.coordination.k);

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

            // Test print, TODO: remove
            System.out.println("Simulation '" + dto.config.runName + "' loaded with "
                    + dto.entities.robots.size() + " robots and "
                    + (dto.tasks != null ? dto.tasks.size() : 0) + " tasks.");

        } catch (IOException e) {
            //TODO: send error message to the frontend
            System.err.println("Error: Could not initialize simulation from file: " + e.getMessage());
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

    /**
     * Runs a tick of the simulation
     *
     * <p>During each tick, the engine:
     * <ul>
     * <li>Collects movement intentions from all robots</li>
     * <li>Applies the active {@link CoordinationPolicy} to those intentions</li>
     * <li>Resolves conflicts and collisions using the {@link CollisionManager}</li>
     * <li>Commits the approved movements by updating the position state of each robot</li>
     * </ul>
     * </p>
     */
    public void tick() {
        // Checking if the warehouse workload has been completed
        if (workloadComplete()) {
            this.running = false;
            return;
        }

        // Assigning tasks to available robots
        dispatcher.assignTasks(robots);

        // Collecting initial move intentions from all robots
        MoveIntention[] intentions = collectIntentions();

        MoveIntention[] coordinatedIntentions;
        if (coordinationPolicy instanceof ReservationKPolicy reservationPolicy) {
            // Reservation_K is engine-managed because it needs shared access to the
            // global reservation table, deterministic path planning, and cleanup hooks.
            coordinatedIntentions = applyReservationWindowPolicy(intentions, reservationPolicy);
        } else {
            // Traffic rules and no-op coordination still use the generic policy hook.
            coordinatedIntentions = coordinationPolicy.apply(intentions);
        }

        // Resolve remaining conflicts after the policy-specific coordination stage.
        MoveIntention[] finalMoveIntentions = collisionManager.resolveConflicts(coordinatedIntentions);

        // Commiting move intentions by updating all robot states
        updateRobotStates(finalMoveIntentions);

        // run per-robot state machine (charging, loading, unloading, energy)
        updateAllRobots();

        if (coordinationPolicy instanceof ReservationKPolicy) {
            // The first slot of each granted window corresponds to the step that was just
            // attempted this tick, so it becomes stale regardless of whether the move
            // succeeded after the final collision pass.
            releaseReservationsForTimeStep(tickCounter + 1);
            // Robots that stopped moving after the state-machine update should not retain
            // any future reservations, because their next path segment will change.
            releaseReservationsForInactiveRobots();
        }

        incrementTickCounter();
    }

    /**
     * Indicates if the warehouse workload has been completed. Returns true if no robot is working
     * on a task and there are no more pending tasks available
     * @return true if the warehouse workload is complete
     */
    private boolean workloadComplete() {
        // Checking if any robot is still working on a task
        for (Robot robot : robots) {
            // There is a robot still working on a task
            if (!robot.isAvailable()) {
                return false;
            }
        }

        return !dispatcher.hasPendingTasks(); // Checking if there are any pending tasks
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
        // Move all robots to 'From' tile in their move intentions
        for (MoveIntention intention : intentions) {
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

    /**
     * Applies the engine-managed Reservation_K policy for the current tick.
     *
     * <p>The engine replans a short path segment for each robot, attempts to reserve
     * a sliding window of future tile-time slots, and only allows the first movement
     * step when the entire requested window is granted.</p>
     *
     * @param intentions the raw intentions collected for the current tick
     * @param reservationPolicy the active Reservation_K policy configuration
     * @return the coordinated intentions to pass into collision resolution
     */
    private MoveIntention[] applyReservationWindowPolicy(MoveIntention[] intentions, ReservationKPolicy reservationPolicy) {
        java.util.Map<UUID, MoveIntention> rawIntentionsByRobot = indexIntentionsByRobot(intentions);
        List<Robot> orderedRobots = getRobotsInReservationOrder();
        List<ReservationRequest> requests = new ArrayList<>(orderedRobots.size());

        // Replanning happens every tick. Each robot drops the future portion of its old
        // window before requesting a fresh sliding window from its current position.
        for (Robot robot : orderedRobots) {
            releaseFutureReservationsForRobot(robot.getId());
            requests.add(buildReservationRequest(robot, rawIntentionsByRobot.get(robot.getId()), reservationPolicy.getK()));
        }

        List<MoveIntention> results = new ArrayList<>(requests.size());
        for (ReservationRequest request : requests) {
            if (request.window().isEmpty()) {
                results.add(request.move());
                continue;
            }

            // Reservation grants are atomic: any conflicting slot forces the whole
            // request to fail and the robot must wait for this tick.
            if (hasReservationConflict(request.robotId(), request.window())) {
                results.add(CoordinationPolicy.forceWait(request.move()));
                continue;
            }

            reserveWindow(request.robotId(), request.window());
            results.add(request.move());
        }

        return results.toArray(new MoveIntention[0]);
    }

    /**
     * Builds a reservation request for a single robot.
     *
     * <p>The request contains the first movement step to execute when successful and
     * the full list of tile-time slots that must be reserved atomically.</p>
     *
     * @param robot the robot requesting reservations
     * @param rawIntention the robot's original intention for the tick, if any
     * @param k the maximum reservation window size
     * @return the reservation request for this robot
     */
    private ReservationRequest buildReservationRequest(Robot robot, MoveIntention rawIntention, int k) {
        MoveIntention wait = createWaitIntention(robot, rawIntention);

        // Reservation windows are only meaningful for robots that are actively moving
        // toward a concrete target.
        if (robot.getState() != RobotState.MOVING || robot.getTarget() == null) {
            return new ReservationRequest(robot.getId(), wait, List.of());
        }

        List<Vector2D> pathSegment = planReservationPathSegment(robot.getPosition(), robot.getTarget(), k);
        if (pathSegment.isEmpty()) {
            return new ReservationRequest(robot.getId(), wait, List.of());
        }

        Tile fromTile = wait.getFromTile();
        Vector2D firstStep = pathSegment.get(0);
        Tile toTile = map.getTile(firstStep.getX(), firstStep.getY());
        MoveIntention move = new MoveIntention(fromTile, toTile, robot);

        List<ReservationKey> window = new ArrayList<>(pathSegment.size());
        for (int i = 0; i < pathSegment.size(); i++) {
            window.add(new ReservationKey(tickCounter + i + 1, pathSegment.get(i)));
        }

        return new ReservationRequest(robot.getId(), move, window);
    }

    /**
     * Creates a wait intention for a robot.
     *
     * @param robot the robot that should wait
     * @param rawIntention the robot's original intention, possibly null
     * @return a wait intention that keeps the robot on its current tile
     */
    private MoveIntention createWaitIntention(Robot robot, MoveIntention rawIntention) {
        if (rawIntention != null && CoordinationPolicy.hasTiles(rawIntention)) {
            return CoordinationPolicy.forceWait(rawIntention);
        }

        Tile currentTile = map.getTile(robot.getPosition().getX(), robot.getPosition().getY());
        return new MoveIntention(currentTile, currentTile, robot);
    }

    /**
     * Plans a deterministic short path segment toward a goal using breadth-first search.
     *
     * <p>The returned path excludes the start position and contains at most {@code k}
     * movement steps. Neighbor expansion follows {@link Map#getNeighbors(Vector2D)} so
     * tie-breaking stays deterministic.</p>
     *
     * @param start the robot's current position
     * @param goal the current navigation goal
     * @param k the maximum number of steps to include in the segment
     * @return a list of future positions representing the next path segment
     */
    private List<Vector2D> planReservationPathSegment(Vector2D start, Vector2D goal, int k) {
        if (start == null || goal == null || start.equals(goal)) {
            return List.of();
        }

        Queue<Vector2D> frontier = new ArrayDeque<>();
        frontier.add(start);

        Set<Vector2D> visited = new HashSet<>();
        visited.add(start);

        java.util.Map<Vector2D, Vector2D> previous = new HashMap<>();

        // Breadth-first search gives a deterministic shortest path because Map.getNeighbors()
        // already returns traversable neighbors in a fixed direction order.
        while (!frontier.isEmpty()) {
            Vector2D current = frontier.remove();
            if (current.equals(goal)) {
                break;
            }

            for (Vector2D neighbor : map.getNeighbors(current)) {
                if (visited.add(neighbor)) {
                    previous.put(neighbor, current);
                    frontier.add(neighbor);
                }
            }
        }

        if (!visited.contains(goal)) {
            return List.of();
        }

        Deque<Vector2D> reversedPath = new ArrayDeque<>();
        Vector2D cursor = goal;
        while (!cursor.equals(start)) {
            reversedPath.push(cursor);
            cursor = previous.get(cursor);
        }

        List<Vector2D> pathSegment = new ArrayList<>(Math.min(k, reversedPath.size()));
        while (!reversedPath.isEmpty() && pathSegment.size() < k) {
            pathSegment.add(reversedPath.pop());
        }

        return pathSegment;
    }

    /**
     * Indexes the current tick's raw intentions by robot id.
     *
     * @param intentions the raw intentions collected by the engine
     * @return a map from robot id to its first non-null intention
     */
    private java.util.Map<UUID, MoveIntention> indexIntentionsByRobot(MoveIntention[] intentions) {
        java.util.Map<UUID, MoveIntention> indexed = new HashMap<>();
        for (MoveIntention intention : CoordinationPolicy.copyNonNull(intentions)) {
            indexed.putIfAbsent(intention.getRobotId(), intention);
        }
        return indexed;
    }

    /**
     * Returns the simulation robots in deterministic reservation-evaluation order.
     *
     * @return the robots sorted by UUID string
     */
    private List<Robot> getRobotsInReservationOrder() {
        List<Robot> ordered = new ArrayList<>(List.of(robots));
        ordered.sort(Comparator.comparing(robot -> robot.getId().toString()));
        return ordered;
    }

    /**
     * Checks whether any requested reservation slot is already owned by another robot.
     *
     * @param robotId the robot requesting the window
     * @param requestedWindow the requested tile-time slots
     * @return true when at least one slot conflicts with another reservation
     */
    private boolean hasReservationConflict(UUID robotId, List<ReservationKey> requestedWindow) {
        for (ReservationKey key : requestedWindow) {
            UUID existingOwner = getReservationOwner(key.timeStep(), key.position());
            if (existingOwner != null && !existingOwner.equals(robotId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reserves every slot in a granted reservation window for a robot.
     *
     * @param robotId the robot receiving the reservation grant
     * @param window the granted tile-time slots
     */
    private void reserveWindow(UUID robotId, List<ReservationKey> window) {
        for (ReservationKey key : window) {
            reservationTable
                    .computeIfAbsent(key.timeStep(), unused -> new HashMap<>())
                    .put(key.position(), robotId);
            reservationsByRobot
                    .computeIfAbsent(robotId, unused -> new HashSet<>())
                    .add(key);
        }
    }

    /**
     * Releases all future reservations owned by a robot after the current tick.
     *
     * @param robotId the robot whose future reservations should be removed
     */
    private void releaseFutureReservationsForRobot(UUID robotId) {
        releaseReservationsMatching(robotId, key -> key.timeStep() > tickCounter);
    }

    /**
     * Releases every reservation slot associated with a completed simulation time step.
     *
     * @param timeStep the absolute time step to clear from the reservation table
     */
    private void releaseReservationsForTimeStep(int timeStep) {
        java.util.Map<Vector2D, UUID> reservationsAtStep = reservationTable.remove(timeStep);
        if (reservationsAtStep == null) {
            return;
        }

        for (java.util.Map.Entry<Vector2D, UUID> entry : reservationsAtStep.entrySet()) {
            ReservationKey key = new ReservationKey(timeStep, entry.getKey());
            removeReservationKey(entry.getValue(), key);
        }
    }

    private void releaseReservationsForInactiveRobots() {
        for (Robot robot : robots) {
            if (robot.getState() != RobotState.MOVING || robot.getTarget() == null) {
                clearReservationsForRobot(robot.getId());
            }
        }
    }

    /**
     * Releases all reservations owned by a robot that satisfy the supplied predicate.
     *
     * @param robotId the robot whose reservations are being inspected
     * @param predicate decides which reservation keys to release
     */
    private void releaseReservationsMatching(UUID robotId, java.util.function.Predicate<ReservationKey> predicate) {
        Set<ReservationKey> ownedReservations = reservationsByRobot.get(robotId);
        if (ownedReservations == null || ownedReservations.isEmpty()) {
            return;
        }

        List<ReservationKey> toRelease = new ArrayList<>();
        for (ReservationKey key : ownedReservations) {
            if (predicate.test(key)) {
                toRelease.add(key);
            }
        }

        for (ReservationKey key : toRelease) {
            removeReservationFromTable(robotId, key);
        }
    }

    /**
     * Removes a reservation from both the global table and the per-robot index.
     *
     * @param robotId the robot that owns the reservation
     * @param key the tile-time slot to remove
     */
    private void removeReservationFromTable(UUID robotId, ReservationKey key) {
        java.util.Map<Vector2D, UUID> reservationsAtStep = reservationTable.get(key.timeStep());
        if (reservationsAtStep != null) {
            UUID currentOwner = reservationsAtStep.get(key.position());
            if (robotId.equals(currentOwner)) {
                reservationsAtStep.remove(key.position());
                if (reservationsAtStep.isEmpty()) {
                    reservationTable.remove(key.timeStep());
                }
            }
        }

        removeReservationKey(robotId, key);
    }

    /**
     * Removes a reservation key from the per-robot index and cleans up empty sets.
     *
     * @param robotId the robot that owns the reservation
     * @param key the reservation key to remove
     */
    private void removeReservationKey(UUID robotId, ReservationKey key) {
        Set<ReservationKey> ownedReservations = reservationsByRobot.get(robotId);
        if (ownedReservations == null) {
            return;
        }

        ownedReservations.remove(key);
        if (ownedReservations.isEmpty()) {
            reservationsByRobot.remove(robotId);
        }
    }

    /**
     * Increments the tick counter for the simulation engine
     */
    private void incrementTickCounter() {
        tickCounter++;
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

    void clearReservationsForRobot(UUID robotId) {
        releaseReservationsMatching(robotId, key -> true);
    }

    /**
     * Returns the robot that owns a given tile-time reservation slot.
     *
     * @param timeStep the absolute time step of the reservation
     * @param position the reserved tile position
     * @return the owning robot id, or null when the slot is free
     */
    UUID getReservationOwner(int timeStep, Vector2D position) {
        java.util.Map<Vector2D, UUID> reservationsAtStep = reservationTable.get(timeStep);
        if (reservationsAtStep == null) {
            return null;
        }
        return reservationsAtStep.get(position);
    }

    int getReservationCountForRobot(UUID robotId) {
        return reservationsByRobot.getOrDefault(robotId, Set.of()).size();
    }
}
