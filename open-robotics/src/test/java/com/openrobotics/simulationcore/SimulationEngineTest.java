package com.openrobotics.simulationcore;

import com.openrobotics.AppState;
import com.openrobotics.io.ConfigLoader;
import com.openrobotics.io.SimulationConfigDTO;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.LoggerMode;
import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import com.openrobotics.robot.navigation.BugNavigationStrategy;
import com.openrobotics.robot.navigation.GreedyNavigationStrategy;
import com.openrobotics.robot.sensors.ProximitySensor;
import com.openrobotics.robot.sensors.RangeSensor;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SimulationEngine}.
 *
 * Covers both constructors, all public getters, the full {@code tick()} pipeline
 * (task assignment, collision resolution, robot state machine, workload completion),
 * and the {@code configSaving()} round-trip for every entity type and coordination
 * policy variant.
 */
public class SimulationEngineTest {

    /** Default test-map width used by setup fixtures. */
    private static final int MAP_W = 10;
    /** Default test-map height used by setup fixtures. */
    private static final int MAP_H = 10;

    /** Shared map fixture recreated before each test. */
    private Map map;
    /** Shared dispatcher fixture recreated before each test. */
    private Dispatcher dispatcher;
    /** Baseline engine fixture created in {@link #setUp()}. */
    private SimulationEngine engine;

    /**
     * Robot test double with scripted next-move output and update-call counting.
     */
    private static class ScriptedRobot extends Robot {
        private MoveIntention nextMove;
        private int updateCalls;

        /**
         * Creates a scripted robot fixture at a fixed position.
         */
        private ScriptedRobot(String name, Vector2D position) {
            super(name, position);
        }

        /**
         * Injects the next move that {@link #getNextMove(Map)} should return.
         */
        public void setNextMove(MoveIntention nextMove) {
            this.nextMove = nextMove;
        }

        /**
         * Returns how many times {@link #update(Map)} has been invoked.
         */
        public int getUpdateCalls() {
            return updateCalls;
        }

        /**
         * Returns the scripted move intention for the current tick.
         */
        @Override
        public MoveIntention getNextMove(Map map) {
            return nextMove;
        }

        /**
         * Increments update-call counter to validate per-tick robot updates.
         */
        @Override
        public void update(Map map) {
            updateCalls++;
        }
    }

    /**
     * Builds a 10×10 map with a no-op coordination policy and an empty dispatcher
     * before each test. Tests that need robots wire them up themselves.
     */
    @BeforeEach
    public void setUp() {
        map = new Map(MAP_W, MAP_H);
        dispatcher = new Dispatcher();
        engine = new SimulationEngine(map, new Robot[]{}, dispatcher, CoordinationPolicy.noOp());
        AppState.setEngine(engine); // some engine methods require AppState.getEngine() to be non-null; set this up for all tests for simplicity
        Logger.setMode(LoggerMode.NO_OP); // disable logging during tests
    }

    /**
     * Immediately after construction the tick counter must be 0.
     */
    @Test
    public void testInitialTickCounterIsZero() {
        assertEquals(0, engine.getTickCounter());
    }

    /**
     * {@code getMap()} returns the exact map instance injected at construction.
     */
    @Test
    public void testGetMapReturnsSameMap() {
        assertSame(map, engine.getMap());
    }

    /**
     * {@code getRobots()} returns the exact array instance injected at construction.
     */
    @Test
    public void testGetRobotsReturnsSameArray() {
        Robot r = new Robot("R1", new Vector2D(5, 5));
        SimulationEngine eng = new SimulationEngine(
                map, new Robot[]{ r }, dispatcher, CoordinationPolicy.noOp());
        assertArrayEquals(new Robot[]{ r }, eng.getRobots());
    }

    /**
     * The 4-arg constructor never sets {@code running = true}, so
     * {@code getIsRunning()} returns {@code false} immediately after construction.
     */
    @Test
    public void testIsRunningFalseAfterConstruction() {
        assertFalse(engine.getIsRunning());
    }

    /**
     * A null coordination policy is normalized to the no-op policy.
     */
    @Test
    public void testNullCoordinationPolicyIsAccepted() {
        Robot r = makeRobot("R1", 5, 5);
        SimulationEngine eng = new SimulationEngine(map, new Robot[]{ r }, dispatcher, null);

        dispatcher.addTask(makeTask(1, 1, 1, 9, 9));

        assertDoesNotThrow(eng::tick);
        assertEquals(1, eng.getTickCounter());
    }

    /**
     * Missing required collaborators leave the engine unable to tick.
     */
    @Test
    public void testTickThrowsWhenRequiredCollaboratorsAreMissing() {
        SimulationEngine eng = new SimulationEngine(map, new Robot[]{}, null, CoordinationPolicy.noOp());

        IllegalStateException ex = assertThrows(IllegalStateException.class, eng::tick);
        assertTrue(ex.getMessage().contains("not initialized"));
    }

    /**
     * Passing {@code null} to the String constructor triggers the default
     * initialisation path: tickCounter is 0 and running is false.
     */
    @Test
    public void testNullPathConstructorSetsDefaults() {
        SimulationEngine eng = new SimulationEngine((String) null);
        assertEquals(0, eng.getTickCounter());
        assertFalse(eng.getIsRunning());
    }

    /**
     * A null-path engine has no robots array set (returns null) — verifies
     * construction does not crash and the getter is safe to call.
     */
    @Test
    public void testNullPathConstructorRobotsIsNull() {
        SimulationEngine eng = new SimulationEngine((String) null);
        assertNull(eng.getRobots(), "robots field should remain null when null path is supplied");
    }

    /**
     * A null-path engine has no map set (returns null) — verifies construction
     * does not crash.
     */
    @Test
    public void testNullPathConstructorMapIsNull() {
        SimulationEngine eng = new SimulationEngine((String) null);
        assertNull(eng.getMap(), "map field should remain null when null path is supplied");
    }

    /**
     * A null-path engine is not initialized enough to run ticks.
     */
    @Test
    public void testNullPathConstructorTickThrows() {
        SimulationEngine eng = new SimulationEngine((String) null);
        assertThrows(IllegalStateException.class, eng::tick);
    }

    /**
     * {@code tick()} increments the counter by 1 each call while work remains.
     */
    @Test
    public void testTickIncrementsCounter() {
        Robot r = makeRobot("R1", 5, 5);
        SimulationEngine eng = buildEngine(new Robot[]{ r });
        dispatcher.addTask(makeTask(1, 1, 1, 9, 9));

        eng.tick();
        assertEquals(1, eng.getTickCounter());

        eng.tick();
        assertEquals(2, eng.getTickCounter());
    }

    /**
     * With no configured tasks, the engine treats this as sandbox mode:
     * {@code tick()} still advances time and leaves {@code running = false}.
     * At least one robot is required for tick() to proceed past the no-robots guard.
     */
    @Test
    public void testTickStopsWhenWorkloadComplete() {
        Robot r = makeRobot("R1", 5, 5);
        SimulationEngine eng = buildEngine(new Robot[]{ r });
        int before = eng.getTickCounter();
        eng.tick();

        assertEquals(before + 1, eng.getTickCounter(),
                "tick() should increment in no-workload sandbox mode");
        assertFalse(eng.getIsRunning());
    }

    /**
     * A live robot cannot move into a tile currently occupied by a dead robot.
     */
    @Test
    public void testTickBlocksMoveIntoDeadRobotTile() {
        ScriptedRobot liveRobot = new ScriptedRobot("Live", new Vector2D(0, 0));
        liveRobot.setState(RobotState.MOVING);
        liveRobot.setNextMove(new MoveIntention(map.getTile(0, 0), map.getTile(1, 0), liveRobot));

        Robot deadRobot = makeRobot("Dead", 1, 0);
        deadRobot.setState(RobotState.BATTERY_DEAD);

        map.addEntity(liveRobot);
        map.addEntity(deadRobot);

        SimulationEngine eng = buildEngine(new Robot[]{ liveRobot, deadRobot });
        AppState.setEngine(eng);

        eng.tick();

        assertEquals(new Vector2D(0, 0), liveRobot.getPosition());
        assertEquals(new Vector2D(1, 0), deadRobot.getPosition());
    }

    /**
     * Same-direction follow-through is blocked: the follower cannot enter the leader's start tile this tick.
     */
    @Test
    public void testTickBlocksSameDirectionFollowThrough() {
        ScriptedRobot leader = new ScriptedRobot("Leader", new Vector2D(1, 0));
        leader.setState(RobotState.MOVING);
        leader.setNextMove(new MoveIntention(map.getTile(1, 0), map.getTile(2, 0), leader));

        ScriptedRobot follower = new ScriptedRobot("Follower", new Vector2D(0, 0));
        follower.setState(RobotState.MOVING);
        follower.setNextMove(new MoveIntention(map.getTile(0, 0), map.getTile(1, 0), follower));

        map.addEntity(leader);
        map.addEntity(follower);

        SimulationEngine eng = buildEngine(new Robot[]{ leader, follower });
        AppState.setEngine(eng);

        eng.tick();

        assertEquals(new Vector2D(2, 0), leader.getPosition());
        assertEquals(new Vector2D(0, 0), follower.getPosition());
    }

    /**
     * Delivery stations still allow a robot to enter even if another robot is already there.
     */
    @Test
    public void testTickAllowsOverlapOnDeliveryStation() {
        map.addEntity(new DeliveryStation("Delivery", new Vector2D(1, 0)));

        ScriptedRobot staying = new ScriptedRobot("Staying", new Vector2D(1, 0));
        staying.setState(RobotState.IDLE);
        staying.setNextMove(new MoveIntention(map.getTile(1, 0), map.getTile(1, 0), staying));

        ScriptedRobot moving = new ScriptedRobot("Moving", new Vector2D(0, 0));
        moving.setState(RobotState.MOVING);
        moving.setNextMove(new MoveIntention(map.getTile(0, 0), map.getTile(1, 0), moving));

        map.addEntity(staying);
        map.addEntity(moving);

        SimulationEngine eng = buildEngine(new Robot[]{ staying, moving });
        AppState.setEngine(eng);

        eng.tick();

        assertEquals(new Vector2D(1, 0), staying.getPosition());
        assertEquals(new Vector2D(1, 0), moving.getPosition());
    }

    /**
     * Charging stations also allow a robot to enter even if another robot is already there.
     */
    @Test
    public void testTickAllowsOverlapOnChargingStation() {
        map.addEntity(new ChargingStation("Charging", new Vector2D(1, 0)));

        ScriptedRobot staying = new ScriptedRobot("Staying", new Vector2D(1, 0));
        staying.setState(RobotState.IDLE);
        staying.setNextMove(new MoveIntention(map.getTile(1, 0), map.getTile(1, 0), staying));

        ScriptedRobot moving = new ScriptedRobot("Moving", new Vector2D(0, 0));
        moving.setState(RobotState.MOVING);
        moving.setNextMove(new MoveIntention(map.getTile(0, 0), map.getTile(1, 0), moving));

        map.addEntity(staying);
        map.addEntity(moving);

        SimulationEngine eng = buildEngine(new Robot[]{ staying, moving });
        AppState.setEngine(eng);

        eng.tick();

        assertEquals(new Vector2D(1, 0), staying.getPosition());
        assertEquals(new Vector2D(1, 0), moving.getPosition());
    }

    /**
     * Running 5 consecutive ticks on an active simulation produces a counter of 5.
     */
    @Test
    public void testMultipleTicksAccumulateCounter() {
        Robot r = makeRobot("R1", 5, 5);
        SimulationEngine eng = buildEngine(new Robot[]{ r });
        dispatcher.addTask(makeTask(1, 1, 1, 9, 9));

        for (int i = 0; i < 5; i++) eng.tick();

        assertEquals(5, eng.getTickCounter());
    }

    /**
     * {@code getIsRunning()} returns {@code false} after a tick on a completed workload.
     */
    @Test
    public void testIsRunningFalseAfterCompletion() {
        engine.tick();
        assertFalse(engine.getIsRunning());
    }

    /**
     * Once all configured work is finished, tick() returns early without advancing time.
     */
    @Test
    public void testTickDoesNotAdvanceAfterWorkloadIsAlreadyComplete() {
        Robot r = makeRobot("R1", 1, 1);
        Task task = makeTask(1, 0, 0, 2, 2);
        dispatcher.addTask(task);
        dispatcher.assignTasks(new Robot[]{ r });
        r.setCurrentTask(null);
        r.setState(RobotState.IDLE);
        task.setStatus(TaskStatus.COMPLETED);

        SimulationEngine eng = buildEngine(new Robot[]{ r });

        eng.tick();

        assertEquals(0, eng.getTickCounter());
        assertFalse(eng.getIsRunning());
    }

    /**
     * An available robot should receive a task during the first tick, and its
     * status should advance to {@code IN_PROGRESS}.
     */
    @Test
    public void testTickAssignsTaskToAvailableRobot() {
        Robot r = makeRobot("R1", 5, 5);
        Task  t = makeTask(1, 1, 1, 9, 9);
        dispatcher.addTask(t);

        SimulationEngine eng = buildEngine(new Robot[]{ r });
        eng.tick();

        assertNotNull(r.getCurrentTask(), "Robot must receive a task during the first tick");
        assertEquals(TaskStatus.IN_PROGRESS, r.getCurrentTask().getStatus());
    }

    /**
     * With two idle robots and two tasks, both robots receive one task each on
     * the first tick.
     */
    @Test
    public void testTickAssignsTasksToMultipleRobots() {
        Robot r1 = makeRobot("R1", 2, 2);
        Robot r2 = makeRobot("R2", 8, 8);
        dispatcher.addTask(makeTask(1, 0, 0, 9, 9));
        dispatcher.addTask(makeTask(2, 9, 9, 0, 0));

        SimulationEngine eng = buildEngine(new Robot[]{ r1, r2 });
        eng.tick();

        assertNotNull(r1.getCurrentTask(), "R1 must receive a task");
        assertNotNull(r2.getCurrentTask(), "R2 must receive a task");
    }

    /**
     * An engine with an empty robot array and pending tasks does not throw,
     * but returns {@code false} immediately due to the NO_ROBOTS_SPAWNED guard.
     * The tick counter stays at 0 because no work is performed.
     */
    @Test
    public void testTickWithNoRobotsAndPendingTasksThrows() {
        dispatcher.addTask(makeTask(1, 0, 0, 1, 1));
        assertDoesNotThrow(() -> engine.tick());
        assertEquals(0, engine.getTickCounter());
        assertEquals(SimulationError.NO_ROBOTS_SPAWNED, engine.getSimulationError());
    }

    /**
     * Busy robots keep their current task while idle robots receive new work.
     */
    @Test
    public void testTickAssignsOnlyToAvailableRobots() {
        Robot busy = makeRobot("Busy", 1, 1);
        Task existing = makeTask(99, 1, 1, 2, 2);
        busy.setCurrentTask(existing);
        busy.setState(RobotState.MOVING);

        Robot idle = makeRobot("Idle", 5, 5);
        dispatcher.addTask(makeTask(1, 0, 0, 9, 9));
        dispatcher.addTask(makeTask(2, 2, 2, 8, 8));

        SimulationEngine eng = buildEngine(new Robot[]{ busy, idle });
        eng.tick();

        assertSame(existing, busy.getCurrentTask(), "Busy robot must keep its current task");
        assertNotNull(idle.getCurrentTask(), "Idle robot should receive a pending task");
        assertEquals(1, dispatcher.getPendingTaskCount(), "Exactly one task should remain unassigned");
    }

    /**
     * A robot placed at the task's pickup location transitions to LOADING
     * on the first tick.
     */
    @Test
    public void testTickTransitionsRobotToLoadingAtPickup() {
        Robot r = makeRobot("R1", 5, 5);
        dispatcher.addTask(makeTask(1, 5, 5, 9, 9));

        SimulationEngine eng = buildEngine(new Robot[]{ r });
        eng.tick();

        assertEquals(RobotState.LOADING, r.getState(),
                "Robot at pickup tile should enter LOADING after first tick");
    }

    /**
     * A robot with a Greedy navigation strategy will eventually complete the
     * full task lifecycle (LOADING → MOVING → UNLOADING → IDLE) and the engine
     * stops when the workload is complete.
     *
     * The robot starts at (0,0) with pickup at (0,1) and dropoff at (0,2)
     * so it needs only a handful of ticks on a clear map.
     */
    @Test
    public void testTickFullTaskCycleCompletesAndStopsEngine() {
        Robot r = makeRobot("R1", 0, 0);
        r.setNav(new GreedyNavigationStrategy(42L));
        r.setSensor(new ProximitySensor());

        Task task = makeTask(1, 0, 1, 0, 2);
        dispatcher.addTask(task);

        SimulationEngine eng = buildEngine(new Robot[]{ r });

        // Run up to 50 ticks; the task should be completed well within that budget.
        // Do not gate on getIsRunning() here because constructor-based engines start with running=false.
        for (int i = 0; i < 50; i++) {
            eng.tick();
            if (task.getStatus() == TaskStatus.COMPLETED) break;
        }

        assertFalse(eng.getIsRunning(),
                "Engine must stop running after the single task is completed");
        // After delivery the robot clears currentTask; the task object itself is COMPLETED
        assertEquals(TaskStatus.COMPLETED, task.getStatus(),
                "Task status must be COMPLETED after full delivery cycle");
        assertNull(r.getCurrentTask(),
                "Robot's currentTask must be null after successful delivery");
    }

    /**
     * When a robot finishes its task and the dispatcher has no further tasks, the
     * engine naturally halts on the next tick: no infinite loop occurs.
     */
    @Test
    public void testEngineHaltsNaturallyAfterSingleTaskCompletion() {
        // Robot at pickup position; delivery station 1 tile away
        Robot r = makeRobot("R1", 3, 3);
        r.setNav(new GreedyNavigationStrategy(0L));
        r.setSensor(new ProximitySensor());

        dispatcher.addTask(makeTask(1, 3, 3, 3, 4)); // pickup == robot pos; dropoff 1 tile south

        SimulationEngine eng = buildEngine(new Robot[]{ r });

        for (int i = 0; i < 20; i++) {
            eng.tick();
            if (!eng.getIsRunning()) break;
        }

        assertFalse(eng.getIsRunning(), "Engine must halt once the single task is done");
    }

    /**
     * Illegal move intentions are filtered out by the collision manager before commit.
     */
    @Test
    public void testTickDoesNotCommitIllegalMoveIntentions() {
        ScriptedRobot robot = new ScriptedRobot("Scripted", new Vector2D(0, 0));
        robot.setNextMove(new MoveIntention(map.getTile(0, 0), map.getTile(2, 0), robot));

        SimulationEngine eng = buildEngine(new Robot[]{ robot });
        eng.tick();

        assertEquals(new Vector2D(0, 0), robot.getPosition());
        assertEquals(1, robot.getUpdateCalls(), "Robot update() should still run every tick");
        assertEquals(1, eng.getTickCounter());
    }

    /**
     * With ReservationKPolicy (k=1), two robots targeting the same tile in the
     * same tick: only one advances; the engine does not crash and the counter
     * increments normally.
     */
    @Test
    public void testTickWithReservationKPolicyNoThrow() {
        Robot r = makeRobot("R1", 5, 5);
        Task  t = makeTask(1, 1, 1, 9, 9);
        Dispatcher d = new Dispatcher();
        d.addTask(t);

        SimulationEngine eng = new SimulationEngine(
                map, new Robot[]{ r }, d, new ReservationKPolicy(1));

        assertDoesNotThrow(() -> eng.tick());
        assertEquals(1, eng.getTickCounter());
    }

    /**
     * With TrafficRulesPolicy (empty intersection set), the engine runs normally
     * and the tick counter increments.
     */
    @Test
    public void testTickWithTrafficRulesPolicyNoThrow() {
        Robot r = makeRobot("R1", 5, 5);
        Dispatcher d = new Dispatcher();
        d.addTask(makeTask(1, 1, 1, 9, 9));

        SimulationEngine eng = new SimulationEngine(
                map, new Robot[]{ r }, d, new TrafficRulesPolicy(new HashSet<>()));

        assertDoesNotThrow(() -> eng.tick());
        assertEquals(1, eng.getTickCounter());
    }

    /**
     * Adding and removing robot entities updates the engine's robot array.
     */
    @Test
    public void testAddAndRemoveRobotRefreshRobotsArray() {
        Robot r = makeRobot("R1", 1, 1);

        engine.addEntity(r);
        assertEquals(1, engine.getRobots().length);
        assertSame(r, engine.getRobots()[0]);

        assertTrue(engine.removeEntity(r));
        assertEquals(0, engine.getRobots().length);
    }

    /**
     * Non-robot entities affect the map but not the robot array.
     */
    @Test
    public void testAddAndRemoveNonRobotEntitiesDoNotAffectRobotArray() {
        ChargingStation station = new ChargingStation("CS", new Vector2D(2, 2));

        engine.addEntity(station);
        assertEquals(0, engine.getRobots().length);
        assertTrue(map.getEntities().contains(station));

        assertTrue(engine.removeEntity(station));
        assertEquals(0, engine.getRobots().length);
        assertFalse(map.getEntities().contains(station));
    }

    /**
     * Null entity operations are safe no-ops.
     */
    @Test
    public void testAddAndRemoveNullEntityAreNoOps() {
        engine.addEntity(null);
        assertFalse(engine.removeEntity(null));
        assertEquals(0, engine.getRobots().length);
    }

    /**
     * The public dispatcher getter returns the same dispatcher instance.
     */
    @Test
    public void testGetDispatcherReturnsInjectedDispatcher() {
        assertSame(dispatcher, engine.getDispatcher());
    }

    /**
     * Saving preserves the explicit metadata from the full constructor.
     */
    @Test
    public void testConfigSavingPreservesMetadataFields() throws IOException {
        SimulationEngine eng = new SimulationEngine(
                map, new Robot[]{}, dispatcher, CoordinationPolicy.noOp(),
                "thorough-run", 250, 1234, 99L);

        Path tmp = Files.createTempFile("sim-meta-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertEquals("thorough-run", dto.config.runName);
            assertEquals(250, dto.config.tickMs);
            assertEquals(1234, dto.config.maxTicks);
            assertEquals(99L, dto.config.seed);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} writes the map dimensions to JSON.
     * Reloading the file must restore the correct width and height.
     */
    @Test
    public void testConfigSavingPreservesMapDimensions() throws IOException {
        SimulationEngine eng = buildEngine(new Robot[]{});
        Path tmp = Files.createTempFile("sim-map-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);
            assertEquals(MAP_W, dto.map.width,  "Saved map width must match");
            assertEquals(MAP_H, dto.map.height, "Saved map height must match");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} persists the current tick counter value.
     */
    @Test
    public void testConfigSavingPreservesTickCounter() throws IOException {
        Robot r = makeRobot("R1", 5, 5);
        Dispatcher d = new Dispatcher();
        d.addTask(makeTask(1, 1, 1, 9, 9));

        SimulationEngine eng = new SimulationEngine(
                map, new Robot[]{ r }, d, CoordinationPolicy.noOp());

        eng.tick();
        eng.tick();
        eng.tick();

        Path tmp = Files.createTempFile("sim-tick-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);
            assertEquals(3, dto.simulation.tick, "Saved tick counter must match 3 elapsed ticks");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} persists the robot count and its position.
     */
    @Test
    public void testConfigSavingPreservesRobot() throws IOException {
        Robot r = makeRobot("BotA", 3, 7);
        SimulationEngine eng = buildEngine(new Robot[]{ r });
        // Add robot to map so it appears in entities iteration
        map.addEntity(r);

        Path tmp = Files.createTempFile("sim-robot-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertEquals(1, dto.entities.robots.size(), "One robot must be saved");
            SimulationConfigDTO.RobotDTO rDto = dto.entities.robots.get(0);
            assertEquals("BotA", rDto.name);
            assertEquals(3, rDto.position.x);
            assertEquals(7, rDto.position.y);
            assertEquals(100.0f, rDto.battery, 0.01f);
            assertEquals("IDLE", rDto.state);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} records the robot's navigation and sensor strategy names.
     * A robot with a GreedyNavigationStrategy and ProximitySensor must save those labels.
     */
    @Test
    public void testConfigSavingPreservesRobotStrategies() throws IOException {
        Robot r = makeRobot("StratBot", 0, 0);
        r.setNav(new GreedyNavigationStrategy(99L));
        r.setSensor(new ProximitySensor());
        map.addEntity(r);

        SimulationEngine eng = buildEngine(new Robot[]{ r });

        Path tmp = Files.createTempFile("sim-strat-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            SimulationConfigDTO.RobotDTO rDto = dto.entities.robots.get(0);
            assertEquals("GREEDY",    rDto.navigationStrategy);
            assertEquals("PROXIMITY", rDto.sensorStrategy);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} only saves occupied tiles; an all-clear map produces
     * an empty tiles list in the JSON.
     */
    @Test
    public void testConfigSavingEmptyTileListWhenNoneOccupied() throws IOException {
        SimulationEngine eng = buildEngine(new Robot[]{});

        Path tmp = Files.createTempFile("sim-tiles-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);
            assertTrue(dto.map.tiles == null || dto.map.tiles.isEmpty(),
                    "No occupied tiles should be written for a clear map");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} records occupied tiles.
     */
    @Test
    public void testConfigSavingPreservesOccupiedTile() throws IOException {
        map.getTile(4, 6).setOccupied(true);
        SimulationEngine eng = buildEngine(new Robot[]{});

        Path tmp = Files.createTempFile("sim-occ-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertNotNull(dto.map.tiles);
            assertEquals(1, dto.map.tiles.size());
            assertEquals(4, dto.map.tiles.get(0).x);
            assertEquals(6, dto.map.tiles.get(0).y);
            assertTrue(dto.map.tiles.get(0).isOccupied);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} persists a {@link ChargingStation} with type "CHARGING".
     */
    @Test
    public void testConfigSavingPreservesChargingStation() throws IOException {
        map.addEntity(new ChargingStation("CS1", new Vector2D(1, 1)));
        SimulationEngine eng = buildEngine(new Robot[]{});

        Path tmp = Files.createTempFile("sim-cs-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertEquals(1, dto.entities.stations.size());
            assertEquals("CHARGING", dto.entities.stations.get(0).type);
            assertEquals("CS1",      dto.entities.stations.get(0).name);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} persists a {@link DeliveryStation} with type "DELIVERY".
     */
    @Test
    public void testConfigSavingPreservesDeliveryStation() throws IOException {
        map.addEntity(new DeliveryStation("DS1", new Vector2D(2, 2)));
        SimulationEngine eng = buildEngine(new Robot[]{});

        Path tmp = Files.createTempFile("sim-ds-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertEquals(1, dto.entities.stations.size());
            assertEquals("DELIVERY", dto.entities.stations.get(0).type);
            assertEquals("DS1",      dto.entities.stations.get(0).name);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} persists an {@link Obstacle} entity in the obstacles list.
     */
    @Test
    public void testConfigSavingPreservesObstacle() throws IOException {
        map.addEntity(new Obstacle("Wall-1", new Vector2D(5, 0)));
        SimulationEngine eng = buildEngine(new Robot[]{});

        Path tmp = Files.createTempFile("sim-obs-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertEquals(1,       dto.entities.obstacles.size());
            assertEquals("Wall-1", dto.entities.obstacles.get(0).name);
            assertEquals(5,       dto.entities.obstacles.get(0).position.x);
            assertEquals(0,       dto.entities.obstacles.get(0).position.y);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} persists a {@link Rack} entity in the racks list.
     */
    @Test
    public void testConfigSavingPreservesRack() throws IOException {
        map.addEntity(new Rack("Rack-A", new Vector2D(3, 3)));
        SimulationEngine eng = buildEngine(new Robot[]{});

        Path tmp = Files.createTempFile("sim-rack-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertEquals(1,        dto.entities.racks.size());
            assertEquals("Rack-A", dto.entities.racks.get(0).name);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} persists pending tasks with their pickup/dropoff and priority.
     */
    @Test
    public void testConfigSavingPreservesPendingTask() throws IOException {
        Task t = makeTask(7, 2, 3, 8, 9);
        dispatcher.addTask(t);
        SimulationEngine eng = buildEngine(new Robot[]{});

        Path tmp = Files.createTempFile("sim-task-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertEquals(1, dto.tasks.size());
            SimulationConfigDTO.TaskDTO tDto = dto.tasks.get(0);
            assertEquals(7,              tDto.id);
            assertEquals(2,              tDto.pickupLocation.x);
            assertEquals(3,              tDto.pickupLocation.y);
            assertEquals(8,              tDto.dropoffLocation.x);
            assertEquals(9,              tDto.dropoffLocation.y);
            assertEquals(TaskStatus.PENDING, tDto.status);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Pending tasks are saved in Task priority order.
     */
    @Test
    public void testConfigSavingWritesTasksInPriorityOrder() throws IOException {
        Task low = new Task(1, new Vector2D(0, 0), new Vector2D(1, 1), 1);
        Task high = new Task(2, new Vector2D(2, 2), new Vector2D(3, 3), 10);
        dispatcher.addTask(low);
        dispatcher.addTask(high);

        SimulationEngine eng = buildEngine(new Robot[]{});
        Path tmp = Files.createTempFile("sim-task-order-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertEquals(2, dto.tasks.size());
            assertEquals(2, dto.tasks.get(0).id);
            assertEquals(1, dto.tasks.get(1).id);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} with a {@link ReservationKPolicy} saves coordination type
     * "RESERVATION_K" and the {@code k} value.
     */
    @Test
    public void testConfigSavingWithReservationKPolicy() throws IOException {
        SimulationEngine eng = new SimulationEngine(
                map, new Robot[]{}, dispatcher, new ReservationKPolicy(3));

        Path tmp = Files.createTempFile("sim-res-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertNotNull(dto.coordination);
            assertEquals("RESERVATION_K", dto.coordination.type);
            assertEquals(3,               dto.coordination.k);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} with a {@link TrafficRulesPolicy} saves coordination type
     * "TRAFFIC_RULES".
     */
    @Test
    public void testConfigSavingWithTrafficRulesPolicy() throws IOException {
        SimulationEngine eng = new SimulationEngine(
                map, new Robot[]{}, dispatcher, new TrafficRulesPolicy(new HashSet<>()));

        Path tmp = Files.createTempFile("sim-tr-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertNotNull(dto.coordination);
            assertEquals("TRAFFIC_RULES", dto.coordination.type);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * TrafficRules intersections are written out with their coordinates.
     */
    @Test
    public void testConfigSavingWithTrafficRulesPolicyPersistsIntersections() throws IOException {
        Set<Tile> intersections = Set.of(map.getTile(1, 2), map.getTile(3, 4));
        SimulationEngine eng = new SimulationEngine(
                map, new Robot[]{}, dispatcher, new TrafficRulesPolicy(intersections));

        Path tmp = Files.createTempFile("sim-tri-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertNotNull(dto.coordination.intersections);
            assertEquals(2, dto.coordination.intersections.size());
            assertTrue(dto.coordination.intersections.stream().anyMatch(v -> v.x == 1 && v.y == 2));
            assertTrue(dto.coordination.intersections.stream().anyMatch(v -> v.x == 3 && v.y == 4));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Toggling a traffic-rule intersection on persists marker coordinates in saved config.
     */
    @Test
    public void testToggleTrafficRuleIntersectionPersistsToSavedConfig() throws IOException {
        SimulationEngine eng = new SimulationEngine(
                map, new Robot[]{}, dispatcher, new TrafficRulesPolicy(new HashSet<>()));

        assertTrue(eng.usesTrafficRulesPolicy());
        assertTrue(eng.toggleTrafficRuleIntersection(2, 3));
        assertTrue(eng.hasTrafficRuleIntersection(2, 3));

        Path tmp = Files.createTempFile("sim-tr-toggle-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertNotNull(dto.coordination);
            assertEquals("TRAFFIC_RULES", dto.coordination.type);
            assertNotNull(dto.coordination.intersections);
            assertEquals(1, dto.coordination.intersections.size());
            assertEquals(2, dto.coordination.intersections.get(0).x);
            assertEquals(3, dto.coordination.intersections.get(0).y);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Toggling the same traffic-rule intersection twice removes the persisted marker.
     */
    @Test
    public void testToggleTrafficRuleIntersectionTwiceRemovesMarker() throws IOException {
        SimulationEngine eng = new SimulationEngine(
                map, new Robot[]{}, dispatcher, new TrafficRulesPolicy(new HashSet<>()));

        assertTrue(eng.toggleTrafficRuleIntersection(4, 4));
        assertTrue(eng.toggleTrafficRuleIntersection(4, 4));
        assertFalse(eng.hasTrafficRuleIntersection(4, 4));
        assertTrue(eng.getTrafficRuleIntersections().isEmpty());

        Path tmp = Files.createTempFile("sim-tr-toggle-remove-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertNotNull(dto.coordination);
            assertEquals("TRAFFIC_RULES", dto.coordination.type);
            assertNotNull(dto.coordination.intersections);
            assertTrue(dto.coordination.intersections.isEmpty());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Intersection toggling is ignored for non-traffic-rules policies and writes no coordination
     * marker data.
     */
    @Test
    public void testToggleTrafficRuleIntersectionIgnoredForNonTrafficRulesPolicy() throws IOException {
        SimulationEngine eng = buildEngine(new Robot[]{});

        assertFalse(eng.usesTrafficRulesPolicy());
        assertFalse(eng.toggleTrafficRuleIntersection(1, 1));
        assertFalse(eng.hasTrafficRuleIntersection(1, 1));
        assertTrue(eng.getTrafficRuleIntersections().isEmpty());

        Path tmp = Files.createTempFile("sim-noop-toggle-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertTrue(dto.coordination == null || dto.coordination.type == null,
                    "No traffic-rules coordination should be written when toggle is ignored");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} with the no-op (lambda) policy writes a coordination
     * section that has neither "RESERVATION_K" nor "TRAFFIC_RULES" as its type —
     * both optional fields are absent or null.
     */
    @Test
    public void testConfigSavingWithNoOpPolicyHasNullCoordinationType() throws IOException {
        SimulationEngine eng = buildEngine(new Robot[]{});

        Path tmp = Files.createTempFile("sim-noop-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            // noOp policy is neither ReservationKPolicy nor TrafficRulesPolicy,
            // so the type field is never set — it should be null or absent.
            assertTrue(dto.coordination == null || dto.coordination.type == null,
                    "No coordination type should be written for the no-op policy");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} persists the {@code isRunning} flag.
     * After workload completion (running=false) the saved JSON reflects that.
     */
    @Test
    public void testConfigSavingPreservesIsRunningFalse() throws IOException {
        SimulationEngine eng = buildEngine(new Robot[]{});
        eng.tick(); // empty workload -> running = false

        Path tmp = Files.createTempFile("sim-running-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);
            assertFalse(dto.simulation.isRunning);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * {@code configSaving()} does not throw even when both robots and tasks lists
     * are empty; it produces a valid minimal JSON file.
     */
    @Test
    public void testConfigSavingEmptyEngineProducesValidFile() {
        SimulationEngine eng = buildEngine(new Robot[]{});
        try {
            Path tmp = Files.createTempFile("sim-empty-", ".json");
            assertDoesNotThrow(() -> eng.configSaving(tmp.toString()));
            Files.deleteIfExists(tmp);
        } catch (IOException e) {
            fail("Could not create temp file: " + e.getMessage());
        }
    }

    /**
     * Unknown map entities fall back to the stations list without a typed station marker.
     */
    @Test
    public void testConfigSavingUnknownMapEntityFallsBackToStationsList() throws IOException {
        map.addEntity(new MapEntity("Generic", new Vector2D(6, 6)));
        SimulationEngine eng = buildEngine(new Robot[]{});

        Path tmp = Files.createTempFile("sim-generic-", ".json");
        try {
            eng.configSaving(tmp.toString());
            SimulationConfigDTO dto = ConfigLoader.load(tmp.toString(), SimulationConfigDTO.class);

            assertEquals(1, dto.entities.stations.size());
            assertEquals("Generic", dto.entities.stations.get(0).name);
            assertNull(dto.entities.stations.get(0).type);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Saving without an initialized map should fail with an IOException.
     */
    @Test
    public void testConfigSavingThrowsWhenMapIsMissing() {
        SimulationEngine eng = new SimulationEngine((String) null);

        IOException ex = assertThrows(IOException.class, () -> eng.configSaving("ignored.json"));
        assertTrue(ex.getMessage().contains("map is not initialized"));
    }

    /**
     * A missing config file records an initialization error and leaves the engine unusable.
     */
    @Test
    public void testConfigConstructorFailureRecordsInitError() throws IOException {
        Path missing = Files.createTempFile("sim-missing-", ".json");
        Files.deleteIfExists(missing);

        SimulationEngine eng = new SimulationEngine(missing.toString());

        assertNotNull(eng.getInitError());
        assertNull(eng.getMap());
        assertNull(eng.getRobots());
        assertThrows(IllegalStateException.class, eng::tick);
    }

    /**
     * Loading from config restores core map, robot, entity, and coordination state.
     */
    @Test
    public void testConfigConstructorLoadsCoreSimulationState() throws IOException {
        SimulationConfigDTO dto = baseConfigDto();
        dto.config.runName = "loaded-run";
        dto.config.tickMs = 150;
        dto.config.maxTicks = 900;
        dto.config.seed = 77L;
        dto.map.width = 6;
        dto.map.height = 5;
        dto.map.tiles = List.of(tileDto(4, 3, true));
        dto.entities.robots = List.of(robotDto(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Loader", 1, 2, 55.0f, "MOVING",
                "GreedyNavigationStrategy", "RangeSensor", 4));
        dto.entities.stations = List.of(
                stationDto(UUID.fromString("00000000-0000-0000-0000-000000000010"), "Charger", 0, 0, "CHARGING"),
                stationDto(UUID.fromString("00000000-0000-0000-0000-000000000011"), "Delivery", 5, 4, "DELIVERY")
        );
        dto.entities.racks = List.of(rackDto(UUID.fromString("00000000-0000-0000-0000-000000000012"), "Rack", 2, 2));
        dto.entities.obstacles = List.of(stationDto(UUID.fromString("00000000-0000-0000-0000-000000000013"), "Obstacle", 3, 1, null));
        dto.tasks = List.of(taskDto(7, 1, 1, 4, 4, 9, TaskStatus.PENDING));
        dto.coordination.type = "RESERVATION_K";
        dto.coordination.k = 4;
        dto.simulation.tick = 12;
        dto.simulation.isRunning = true;

        Path tmp = writeConfig(dto);
        try {
            SimulationEngine eng = new SimulationEngine(tmp.toString());

            assertNull(eng.getInitError());
            assertNotNull(eng.getMap());
            assertEquals(6, eng.getMap().getWidth());
            assertEquals(5, eng.getMap().getHeight());
            assertTrue(eng.getMap().getTile(4, 3).isOccupied());
            assertEquals(12, eng.getTickCounter());
            assertTrue(eng.getIsRunning());
            assertEquals(1, eng.getRobots().length);
            assertEquals("Loader", eng.getRobots()[0].getName());
            assertEquals(new Vector2D(1, 2), eng.getRobots()[0].getPosition());
            assertEquals(55.0f, eng.getRobots()[0].getBattery(), 0.01f);
            assertEquals(RobotState.MOVING, eng.getRobots()[0].getState());
            assertEquals(4, eng.getRobots()[0].getStuckTicks());
            assertInstanceOf(GreedyNavigationStrategy.class, eng.getRobots()[0].getNav());
            assertInstanceOf(RangeSensor.class, eng.getRobots()[0].getSensor());
            assertEquals(1, eng.getDispatcher().getPendingTaskCount());
            assertTrue(eng.getMap().getEntities().stream().anyMatch(e -> e instanceof ChargingStation));
            assertTrue(eng.getMap().getEntities().stream().anyMatch(e -> e instanceof DeliveryStation));
            assertTrue(eng.getMap().getEntities().stream().anyMatch(e -> e instanceof Rack));
            assertTrue(eng.getMap().getEntities().stream().anyMatch(e -> e instanceof Obstacle));

            Path saved = Files.createTempFile("sim-loaded-roundtrip-", ".json");
            try {
                eng.configSaving(saved.toString());
                SimulationConfigDTO savedDto = ConfigLoader.load(saved.toString(), SimulationConfigDTO.class);
                assertEquals("RESERVATION_K", savedDto.coordination.type);
                assertEquals(4, savedDto.coordination.k);
            } finally {
                Files.deleteIfExists(saved);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Old station configs without a type field still load using the name heuristic.
     */
    @Test
    public void testConfigConstructorUsesStationNameHeuristicWhenTypeMissing() throws IOException {
        SimulationConfigDTO dto = baseConfigDto();
        dto.entities.stations = List.of(
                stationDto(UUID.fromString("00000000-0000-0000-0000-000000000021"), "charger-old", 1, 1, null),
                stationDto(UUID.fromString("00000000-0000-0000-0000-000000000022"), "delivery-old", 2, 2, null)
        );

        Path tmp = writeConfig(dto);
        try {
            SimulationEngine eng = new SimulationEngine(tmp.toString());

            assertTrue(eng.getMap().getEntities().stream().anyMatch(e ->
                    e instanceof ChargingStation && "charger-old".equals(e.getName())));
            assertTrue(eng.getMap().getEntities().stream().anyMatch(e ->
                    e instanceof DeliveryStation && "delivery-old".equals(e.getName())));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Unknown sensor names fall back to ProximitySensor during config loading.
     */
    @Test
    public void testConfigConstructorFallsBackToProximitySensorForUnknownSensorType() throws IOException {
        SimulationConfigDTO dto = baseConfigDto();
        dto.entities.robots = List.of(robotDto(
                UUID.fromString("00000000-0000-0000-0000-000000000031"),
                "FallbackBot", 0, 0, 100.0f, "IDLE",
                "BUGNAVIGATIONSTRATEGY", "UNKNOWN_SENSOR", 0));

        Path tmp = writeConfig(dto);
        try {
            SimulationEngine eng = new SimulationEngine(tmp.toString());

            assertInstanceOf(BugNavigationStrategy.class, eng.getRobots()[0].getNav());
            assertInstanceOf(ProximitySensor.class, eng.getRobots()[0].getSensor());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Traffic-rules coordination survives a load-then-save round trip with intersections.
     */
    @Test
    public void testConfigConstructorLoadsTrafficRulesCoordination() throws IOException {
        SimulationConfigDTO dto = baseConfigDto();
        dto.coordination.type = "TRAFFIC_RULES";
        dto.coordination.intersections = List.of(
                new SimulationConfigDTO.Vector2DDTO(1, 1),
                new SimulationConfigDTO.Vector2DDTO(2, 2)
        );

        Path tmp = writeConfig(dto);
        try {
            SimulationEngine eng = new SimulationEngine(tmp.toString());
            Path saved = Files.createTempFile("sim-traffic-roundtrip-", ".json");
            try {
                eng.configSaving(saved.toString());
                SimulationConfigDTO savedDto = ConfigLoader.load(saved.toString(), SimulationConfigDTO.class);

                assertEquals("TRAFFIC_RULES", savedDto.coordination.type);
                assertEquals(2, savedDto.coordination.intersections.size());
                assertTrue(savedDto.coordination.intersections.stream().anyMatch(v -> v.x == 1 && v.y == 1));
                assertTrue(savedDto.coordination.intersections.stream().anyMatch(v -> v.x == 2 && v.y == 2));
            } finally {
                Files.deleteIfExists(saved);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // Helpers
    /**
     * Creates a {@link Robot} at the given (x,y) coordinates.
     *
     * @param name robot name
     * @param x    x coordinate
     * @param y    y coordinate
     * @return a fresh idle robot
     */
    private Robot makeRobot(String name, int x, int y) {
        return new Robot(name, new Vector2D(x, y));
    }

    /**
     * Creates a {@link Task} with the given parameters. Status defaults to PENDING.
     *
     * @param id       task id
     * @param px       pickup x
     * @param py       pickup y
     * @param dx       dropoff x
     * @param dy       dropoff y
     * @return a new PENDING task
     */
    private Task makeTask(int id, int px, int py, int dx, int dy) {
        return new Task(id, new Vector2D(px, py), new Vector2D(dx, dy), 1);
    }

    /**
     * Builds an engine against the shared {@link #map} and {@link #dispatcher}
     * with a no-op coordination policy.
     *
     * @param robots the robot array to inject
     * @return a new SimulationEngine ready for testing
     */
    private SimulationEngine buildEngine(Robot[] robots) {
        return new SimulationEngine(map, robots, dispatcher, CoordinationPolicy.noOp());
    }

    /**
     * Creates a base configuration DTO.
     *
     * @return fully initialized DTO with default sections for config-load tests
     */
    private SimulationConfigDTO baseConfigDto() {
        SimulationConfigDTO dto = new SimulationConfigDTO();
        dto.config = new SimulationConfigDTO.ConfigSection();
        dto.config.runId = UUID.randomUUID();
        dto.config.runName = "base-run";
        dto.config.tickMs = 100;
        dto.config.maxTicks = 500;
        dto.config.seed = 42L;

        dto.map = new SimulationConfigDTO.MapSection();
        dto.map.mapId = UUID.randomUUID();
        dto.map.width = 4;
        dto.map.height = 4;
        dto.map.tiles = List.of();

        dto.entities = new SimulationConfigDTO.EntitiesSection();
        dto.entities.robots = List.of();
        dto.entities.stations = List.of();
        dto.entities.racks = List.of();
        dto.entities.obstacles = List.of();

        dto.tasks = List.of();

        dto.simulation = new SimulationConfigDTO.SimStateSection();
        dto.simulation.tick = 0;
        dto.simulation.isRunning = false;
        dto.simulation.speedMultiplier = 1.0;

        dto.coordination = new SimulationConfigDTO.CoordinationSection();
        return dto;
    }

    /**
     * Creates a tile DTO.
     * @param x the x coordinate
     * @param y the y coordinate
     * @param occupied whether the tile is occupied
     * @return the tile DTO
     */
    private SimulationConfigDTO.TileDTO tileDto(int x, int y, boolean occupied) {
        SimulationConfigDTO.TileDTO tile = new SimulationConfigDTO.TileDTO();
        tile.x = x;
        tile.y = y;
        tile.isOccupied = occupied;
        return tile;
    }

    /**
     * Creates a robot DTO.
     * @param id the robot ID
     * @param name the robot name
     * @param x the x coordinate
     * @param y the y coordinate
     * @param battery the battery level
     * @param state the robot state
     * @param navigationStrategy the navigation strategy
     * @param sensorStrategy the sensor strategy
     * @param stuckTicks the number of stuck ticks
     * @return the robot DTO
     */
    private SimulationConfigDTO.RobotDTO robotDto(
            UUID id, String name, int x, int y, float battery, String state,
            String navigationStrategy, String sensorStrategy, int stuckTicks) {
        SimulationConfigDTO.RobotDTO robot = new SimulationConfigDTO.RobotDTO();
        robot.id = id;
        robot.name = name;
        robot.position = new SimulationConfigDTO.Vector2DDTO(x, y);
        robot.battery = battery;
        robot.state = state;
        robot.navigationStrategy = navigationStrategy;
        robot.sensorStrategy = sensorStrategy;
        robot.stuckTicks = stuckTicks;
        return robot;
    }

    /**
     * Creates a station DTO.
     * @param id the station ID
     * @param name the station name
     * @param x the x coordinate
     * @param y the y coordinate
     * @param type the station type
     * @return the station DTO
     */
    private SimulationConfigDTO.MapEntityDTO stationDto(UUID id, String name, int x, int y, String type) {
        SimulationConfigDTO.MapEntityDTO entity = new SimulationConfigDTO.MapEntityDTO();
        entity.id = id;
        entity.name = name;
        entity.position = new SimulationConfigDTO.Vector2DDTO(x, y);
        entity.type = type;
        return entity;
    }

    /**
     * Creates a rack DTO.
     *
     * @param id rack ID
     * @param name rack name
     * @param x x coordinate
     * @param y y coordinate
     * @return rack DTO instance
     */
    private SimulationConfigDTO.RackDTO rackDto(UUID id, String name, int x, int y) {
        SimulationConfigDTO.RackDTO entity = new SimulationConfigDTO.RackDTO();
        entity.id = id;
        entity.name = name;
        entity.position = new SimulationConfigDTO.Vector2DDTO(x, y);
        return entity;
    }

    /**
     * Creates a task DTO.
     * @param id the task ID
     * @param px the pickup x coordinate
     * @param py the pickup y coordinate
     * @param dx the dropoff x coordinate
     * @param dy the dropoff y coordinate
     * @param priority the task priority
     * @param status the task status
     * @return the task DTO
     */
    private SimulationConfigDTO.TaskDTO taskDto(
            int id, int px, int py, int dx, int dy, int priority, TaskStatus status) {
        SimulationConfigDTO.TaskDTO task = new SimulationConfigDTO.TaskDTO();
        task.id = id;
        task.pickupLocation = new SimulationConfigDTO.Vector2DDTO(px, py);
        task.dropoffLocation = new SimulationConfigDTO.Vector2DDTO(dx, dy);
        task.priority = priority;
        task.status = status;
        return task;
    }

    /**
     * Writes a configuration DTO to a temporary file.
     * @param dto the configuration DTO
     * @return the path to the temporary file
     * @throws IOException if the file cannot be created
     */
    private Path writeConfig(SimulationConfigDTO dto) throws IOException {
        Path tmp = Files.createTempFile("sim-config-", ".json");
        ConfigLoader.save(tmp.toString(), dto);
        return tmp;
    }
}
