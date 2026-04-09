package com.openrobotics.robot;

import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.robot.sensors.Sensor;
import com.openrobotics.robot.sensors.SensorStrategy;
import com.openrobotics.simulationcore.MoveIntention;
import com.openrobotics.task.Task;
import com.openrobotics.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended unit tests for {@link Robot}.
 *
 * <p>Supplements the basic {@code RobotTest} with deeper coverage of the state
 * machine ({@code update()}), navigation target resolution ({@code getTarget()}),
 * charging behaviour, task completion transitions, and the {@code getNextMove()}
 * guard clauses.
 */
public class RobotAdvancedTest {

    private static final int MAP_SIZE = 10;
    private Map map;
    private Robot robot;

    /**
     * Creates a fresh 10×10 map and a robot at (5,5) before each test.
     */
    @BeforeEach
    public void setUp() {
        map   = new Map(MAP_SIZE, MAP_SIZE);
        robot = new Robot("Bot", new Vector2D(5, 5));
    }

    /**
     * getTarget() returns null when there is no task and no charger override.
     */
    @Test
    public void testGetTargetNullWhenNoTask() {
        assertNull(robot.getTarget());
    }

    /**
     * getTarget() returns the pickup location when a task is assigned and the
     * robot has not yet picked up the item.
     */
    @Test
    public void testGetTargetReturnsPickupBeforePickup() {
        Task task = new Task(1, new Vector2D(2, 2), new Vector2D(8, 8), 1);
        robot.setCurrentTask(task);
        assertEquals(new Vector2D(2, 2), robot.getTarget());
    }

    /**
     * getTarget() returns the dropoff location after pickup is done.
     */
    @Test
    public void testGetTargetReturnsDropoffAfterPickup() {
        Task task = new Task(1, new Vector2D(2, 2), new Vector2D(8, 8), 1);
        robot.setCurrentTask(task);
        robot.setHasPickedUp(true);
        assertEquals(new Vector2D(8, 8), robot.getTarget());
    }

    /**
     * The UUID-loading constructor should preserve the provided ID.
     */
    @Test
    public void testUuidConstructorPreservesProvidedId() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000123");
        Robot loaded = new Robot(id, "LoadedBot", new Vector2D(1, 2));

        assertEquals(id, loaded.getId());
        assertEquals(RobotState.IDLE, loaded.getState());
        assertNull(loaded.getCurrentTask());
    }

    /**
     * Each update() tick in CHARGING state adds 5% battery.
     */
    @Test
    public void testChargingIncrementsBattery() {
        robot.setBattery(10.0f);
        robot.setState(RobotState.CHARGING);
        robot.update(null);
        assertEquals(15.0f, robot.getBattery(), 0.001f);
    }

    /**
     * Battery is capped at 100 during charging.
     */
    @Test
    public void testChargingCapsAt100() {
        robot.setBattery(98.0f);
        robot.setState(RobotState.CHARGING);
        robot.update(null);
        assertEquals(100.0f, robot.getBattery(), 0.001f,
                "Battery must not exceed 100 when charging");
    }

    /**
     * Charging ticks contribute to the lifetime charging counter.
     */
    @Test
    public void testChargingIncrementsChargingTickCounter() {
        robot.setBattery(10.0f);
        robot.setState(RobotState.CHARGING);

        robot.update(null);

        assertEquals(1, robot.getTotalChargingTicks());
    }

    /**
     * When fully charged and a task is assigned, the robot transitions to MOVING.
     */
    @Test
    public void testChargingTransitionsToMovingWhenFullAndHasTask() {
        Task task = new Task(1, new Vector2D(1, 1), new Vector2D(2, 2), 1);
        robot.setCurrentTask(task);
        robot.setState(RobotState.CHARGING);
        robot.setBattery(95.0f); // will reach 100 after one tick

        robot.update(null);
        assertEquals(RobotState.MOVING, robot.getState(),
                "Fully charged robot with a task must transition to MOVING");
    }

    /**
     * When fully charged and no task, the robot transitions to IDLE.
     */
    @Test
    public void testChargingTransitionsToIdleWhenFullAndNoTask() {
        robot.setState(RobotState.CHARGING);
        robot.setBattery(95.0f);
        robot.update(null);
        assertEquals(RobotState.IDLE, robot.getState(),
                "Fully charged robot without a task must transition to IDLE");
    }

    /**
     * LOADING decrements the loading timer each tick.
     * After DEFAULT_LOADING_TICKS (1) ticks, the robot transitions to MOVING
     * and hasPickedUp becomes true.
     */
    @Test
    public void testLoadingCompletesAfterOneTick() {
        Map map = new Map(10, 10);
        // Robot is placed at the pickup location. The first update() in MOVING
        // state detects arrival and transitions to LOADING (setting loadingTicksRemaining=1).
        // The second update() completes loading and transitions to MOVING with hasPickedUp=true.
        Task task = new Task(1, new Vector2D(5, 5), new Vector2D(8, 8), 1);
        robot.setCurrentTask(task);
        robot.setState(RobotState.MOVING);

        // update() while at pickup position should transition to LOADING
        robot.update(map);  // MOVING → checks arrival at (5,5) → LOADING
        assertEquals(RobotState.LOADING, robot.getState());

        // one more update() should complete loading → MOVING, hasPickedUp = true
        robot.update(map);
        assertEquals(RobotState.MOVING, robot.getState());
        assertTrue(robot.isHasPickedUp());
    }

    /**
     * Arrival at pickup initializes the loading timer.
     */
    @Test
    public void testArrivalAtPickupSetsLoadingTicksRemaining() {
        Map map = new Map(10, 10);
        Task task = new Task(1, new Vector2D(5, 5), new Vector2D(8, 8), 1);
        robot.setCurrentTask(task);
        robot.setState(RobotState.MOVING);

        robot.update(map);

        assertEquals(RobotState.LOADING, robot.getState());
        assertEquals(1, robot.getLoadingTicksRemaining());
    }

    /**
     * UNLOADING completes after one tick: task is marked COMPLETED, robot goes IDLE,
     * currentTask is cleared, and hasPickedUp resets.
     */
    @Test
    public void testUnloadingCompletesAfterOneTick() {
        Map map = new Map(10, 10);
        Task task = new Task(1, new Vector2D(1, 1), new Vector2D(5, 5), 1);
        robot.setCurrentTask(task);
        robot.setHasPickedUp(true);
        robot.setState(RobotState.MOVING);

        // Trigger arrival at dropoff (robot is already at (5,5))
        robot.update(map); // MOVING → arrives at dropoff (5,5) → UNLOADING

        assertEquals(RobotState.UNLOADING, robot.getState());

        robot.update(map); // UNLOADING → IDLE
        assertEquals(RobotState.IDLE,       robot.getState());
        assertNull(robot.getCurrentTask(),   "currentTask must be null after delivery");
        assertFalse(robot.isHasPickedUp(),   "hasPickedUp must reset after delivery");
        assertEquals(TaskStatus.COMPLETED,  task.getStatus());
    }

    /**
     * Arrival at dropoff initializes the unloading timer.
     */
    @Test
    public void testArrivalAtDropoffSetsUnloadingTicksRemaining() {
        Map map = new Map(10, 10);
        Task task = new Task(1, new Vector2D(1, 1), new Vector2D(5, 5), 1);
        robot.setCurrentTask(task);
        robot.setHasPickedUp(true);
        robot.setState(RobotState.MOVING);

        robot.update(map);

        assertEquals(RobotState.UNLOADING, robot.getState());
        assertEquals(1, robot.getUnloadingTicksRemaining());
    }

    /**
     * Completing delivery increments the lifetime completed-task counter.
     */
    @Test
    public void testUnloadingIncrementsCompletedTaskCounter() {
        Map map = new Map(10, 10);
        Task task = new Task(1, new Vector2D(1, 1), new Vector2D(5, 5), 1);
        robot.setCurrentTask(task);
        robot.setHasPickedUp(true);
        robot.setState(RobotState.MOVING);

        robot.update(map);
        robot.update(map);

        assertEquals(1, robot.getTasksCompleted());
    }

    /**
     * A robot that physically moved this tick should consume 1 energy unit and
     * reset stuckTicks to 0.
     */
    @Test
    public void testMovingConsumesBatteryWhenActuallyMoved() {
        robot.setState(RobotState.MOVING);
        // Simulate a previous position different from the current one
        // by calling getNextMove() which saves previousPosition, then
        // manually update position to simulate movement.
        // We use the public API: set previousPosition via getNextMove on a
        // minimal map, then move the robot.

        // Arrange: call getNextMove to save previousPosition as (5,5)
        @SuppressWarnings("unused")
        MoveIntention intention = robot.getNextMove(map);
        // Now move the robot to (6,5)
        robot.setPosition(new Vector2D(6, 5));
        float batteryBefore = robot.getBattery();

        robot.update(null);

        assertEquals(batteryBefore - 1.0f, robot.getBattery(), 0.001f,
                "Moving robot must lose 1 energy unit per tick");
        assertEquals(0, robot.getStuckTicks(),
                "stuckTicks must reset to 0 when robot moves");
    }

    /**
     * A successful move updates the movement statistics.
     */
    @Test
    public void testMovingUpdatesLifetimeMovementStatistics() {
        robot.setState(RobotState.MOVING);
        robot.getNextMove(map);
        robot.setPosition(new Vector2D(6, 5));

        robot.update(null);

        assertEquals(1, robot.getTotalMovingTicks());
        assertEquals(1, robot.getTotalDistanceMoved());
        assertEquals(1.0f, robot.getTotalEnergyConsumed(), 0.001f);
    }

    /**
     * A robot that did not move this tick should increment stuckTicks.
     */
    @Test
    public void testStuckTicksIncrementWhenNotMoved() {
        robot.setState(RobotState.MOVING);
        // Call getNextMove to save previousPosition == current position
        robot.getNextMove(map);
        // Robot stays at same position, then update()
        robot.update(null);

        assertTrue(robot.getStuckTicks() >= 1,
                "stuckTicks must increment when robot does not move");
    }

    /**
     * IDLE ticks contribute to the lifetime idle counter.
     */
    @Test
    public void testIdleUpdateIncrementsIdleTickCounter() {
        robot.setState(RobotState.IDLE);

        robot.update(null);

        assertEquals(1, robot.getTotalIdleTicks());
    }

    /**
     * A robot is available if and only if it is IDLE with no current task.
     */
    @Test
    public void testAvailableOnlyWhenIdleAndNoTask() {
        assertTrue(robot.isAvailable());

        robot.setState(RobotState.MOVING);
        assertFalse(robot.isAvailable());

        robot.setState(RobotState.IDLE);
        robot.setCurrentTask(new Task(1, new Vector2D(0,0), new Vector2D(1,1), 1));
        assertFalse(robot.isAvailable(), "Robot with a task is not available even if IDLE");
    }

    /**
     * An IDLE robot that has a current task should be promoted to MOVING and
     * return a non-null MoveIntention when getNextMove() is called.
     */
    @Test
    public void testGetNextMovePromotesIdleRobotWithTask() {
        Task task = new Task(1, new Vector2D(1, 1), new Vector2D(8, 8), 1);
        robot.setCurrentTask(task);
        // robot is IDLE but has a task
        robot.getNextMove(map);
        assertEquals(RobotState.MOVING, robot.getState(),
                "IDLE robot with a task should be promoted to MOVING by getNextMove()");
    }

    /**
     * If no navigation strategy is configured, a moving robot stays in place.
     */
    @Test
    public void testGetNextMoveWithoutNavigationReturnsStayIntention() {
        robot.setState(RobotState.MOVING);
        robot.setCurrentTask(new Task(1, new Vector2D(7, 7), new Vector2D(8, 8), 1));

        MoveIntention intention = robot.getNextMove(map);

        assertSame(intention.getFromTile(), intention.getToTile());
        assertEquals(new Vector2D(5, 5), robot.getPreviousPosition());
    }

    /**
     * A configured navigation strategy is used when the robot has a target.
     */
    @Test
    public void testGetNextMoveDelegatesToNavigationStrategy() {
        robot.setCurrentTask(new Task(1, new Vector2D(7, 5), new Vector2D(8, 8), 1));
        robot.setState(RobotState.MOVING);
        robot.setNav((currentRobot, currentMap) ->
                new MoveIntention(currentMap.getTile(5, 5), currentMap.getTile(6, 5), currentRobot));

        MoveIntention intention = robot.getNextMove(map);

        assertEquals(5, intention.getFromTile().getX());
        assertEquals(6, intention.getToTile().getX());
        assertSame(robot, intention.getRobot());
    }

    /**
     * A configured sensor strategy should populate the last scan before movement logic runs.
     */
    @Test
    public void testGetNextMoveStoresLastScanFromSensorStrategy() {
        Sensor scan = new Sensor(List.of());
        SensorStrategy sensor = (currentRobot, currentMap) -> scan;
        robot.setSensor(sensor);

        robot.getNextMove(map);

        assertSame(scan, robot.getLastScan());
    }

    /**
     * A robot in CHARGING state returns a stay-in-place intention.
     */
    @Test
    public void testGetNextMoveChargingReturnsStayIntention() {
        robot.setState(RobotState.CHARGING);
        MoveIntention intention = robot.getNextMove(map);
        assertNotNull(intention);
        assertSame(intention.getFromTile(), intention.getToTile(),
                "Charging robot must produce a stay-in-place intention");
    }

    /**
     * An invalid robot position should fail fast when generating a move.
     */
    @Test
    public void testGetNextMoveThrowsWhenRobotIsOffMap() {
        robot.setPosition(new Vector2D(-1, 5));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> robot.getNextMove(map));
        assertTrue(ex.getMessage().contains("-1,5"));
    }

    /**
     * When battery drops below the low-battery threshold, the robot attempts to
     * find a charging station; if one exists on the map it becomes the navigation target.
     */
    @Test
    public void testLowBatteryRedirectsToCharger() {
        ChargingStation cs = new ChargingStation("CS", new Vector2D(1, 1));
        map.addEntity(cs);

        robot.setBattery(10.0f); // below LOW_BATTERY_THRESHOLD (20)
        // Giving the robot a task so it's in MOVING state
        Task task = new Task(1, new Vector2D(8, 8), new Vector2D(9, 9), 1);
        robot.setCurrentTask(task);
        robot.setState(RobotState.MOVING);

        robot.getNextMove(map);

        assertEquals(new Vector2D(1, 1), robot.getTarget(),
                "Low-battery robot should target the nearest charging station");
    }

    /**
     * If already on a charging station with low battery, the robot should enter CHARGING immediately.
     */
    @Test
    public void testLowBatteryOnChargingStationTransitionsToCharging() {
        ChargingStation cs = new ChargingStation("CS", new Vector2D(5, 5));
        map.addEntity(cs);

        robot.setBattery(10.0f);
        robot.setCurrentTask(new Task(1, new Vector2D(8, 8), new Vector2D(9, 9), 1));
        robot.setState(RobotState.MOVING);

        MoveIntention intention = robot.getNextMove(map);

        assertEquals(RobotState.CHARGING, robot.getState());
        assertSame(intention.getFromTile(), intention.getToTile());
    }

    /**
     * If no charging station exists, a low-battery robot gives up moving and becomes IDLE.
     */
    @Test
    public void testLowBatteryWithoutChargingStationBecomesIdle() {
        robot.setBattery(10.0f);
        robot.setCurrentTask(new Task(1, new Vector2D(8, 8), new Vector2D(9, 9), 1));
        robot.setState(RobotState.MOVING);

        MoveIntention intention = robot.getNextMove(map);

        assertEquals(RobotState.IDLE, robot.getState());
        assertSame(intention.getFromTile(), intention.getToTile());
    }

    /**
     * Once battery is healthy again, the robot should target its task instead of a charger override.
     */
    @Test
    public void testHealthyBatteryClearsPreviousChargerOverride() {
        map.addEntity(new ChargingStation("CS", new Vector2D(1, 1)));
        Task task = new Task(1, new Vector2D(8, 8), new Vector2D(9, 9), 1);
        robot.setCurrentTask(task);
        robot.setState(RobotState.MOVING);

        robot.setBattery(10.0f);
        robot.getNextMove(map);
        assertEquals(new Vector2D(1, 1), robot.getTarget());

        robot.setBattery(100.0f);

        robot.getNextMove(map);
        assertEquals(new Vector2D(8, 8), robot.getTarget());
    }

    /**
     * toString() must include the robot's name, position, and state.
     */
    @Test
    public void testToStringContainsKeyFields() {
        String s = robot.toString();
        assertTrue(s.contains("Bot"), "toString must contain robot name");
        assertTrue(s.contains("5"), "toString must contain position coordinate");
        assertTrue(s.contains("IDLE"), "toString must contain state");
    }
}
