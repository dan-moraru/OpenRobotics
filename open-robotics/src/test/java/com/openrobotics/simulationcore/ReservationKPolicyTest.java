package com.openrobotics.simulationcore;

import com.openrobotics.map.Map;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openrobotics.robot.RobotState;
import com.openrobotics.robot.navigation.NavigationStrategy;
import com.openrobotics.task.Task;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ReservationKPolicy}.
 *
 * <p>Verifies constructor validation, the k value getter, the single-tile
 * reservation mechanism, conflict forcing (second robot to same tile waits),
 * per-tick reservation reset, stay-in-place pass-through, and null/empty
 * input handling.
 */
public class ReservationKPolicyTest {

    private ReservationKPolicy policy;

    /**
     * Creates a fresh k=1 policy before every test.
     */
    @BeforeEach
    public void setUp() {
        policy = new ReservationKPolicy(1);
    }

    // Helpers

    private static Robot makeRobot(String name) {
        return new Robot(name, new Vector2D(0, 0));
    }

    private static MoveIntention move(Robot robot, int fromX, int fromY, int toX, int toY) {
        return new MoveIntention(new Tile(fromX, fromY), new Tile(toX, toY), robot);
    }

    private static MoveIntention stay(Robot robot, int x, int y) {
        Tile t = new Tile(x, y);
        return new MoveIntention(t, t, robot);
    }

    private static Map testMap() {
        return new Map(20, 20);
    }

        /**
     * Creates a robot with a target task and moving state.
     * @param name the name of the robot
     * @param id the id of the robot
     * @param start the starting position of the robot
     * @param target the target position of the robot
     * @return the robot
     */
    private static Robot movingRobot(String name, UUID id, Vector2D start, Vector2D target) {
        Robot robot = new Robot(id, name, start);
        robot.setCurrentTask(new Task(id.hashCode(), target, target, 1));
        robot.setState(RobotState.MOVING);
        return robot;
    }

    /**
     * Creates a move intention for a robot to move from one tile to another.
     * @param map the map
     * @param robot the robot
     * @param fromX the x coordinate of the from tile
     * @param fromY the y coordinate of the from tile
     * @param toX the x coordinate of the to tile
     * @param toY the y coordinate of the to tile
     * @return the move intention
     */
    private static MoveIntention move(Map map, Robot robot, int fromX, int fromY, int toX, int toY) {
        Tile from = map.getTile(fromX, fromY);
        Tile to = map.getTile(toX, toY);
        return new MoveIntention(from, to, robot);
    }

    /**
     * Asserts that a move intention is in the list of intentions for a robot.
     * @param intentions the list of intentions
     * @param robot the robot
     * @param expectedX the expected x coordinate of the to tile
     * @param expectedY the expected y coordinate of the to tile
     */
    private static void assertMove(MoveIntention[] intentions, Robot robot, int expectedX, int expectedY) {
        for (MoveIntention intention : intentions) {
            if (intention.getRobot().getId().equals(robot.getId())) {
                assertEquals(expectedX, intention.getToTile().getX());
                assertEquals(expectedY, intention.getToTile().getY());
                return;
            }
        }

        throw new AssertionError("No move intention found for robot " + robot.getName());
    }

    /**
     * A navigation strategy that moves the robot one step towards its target in the one-row corridor.
     */
    private static class CorridorStepNavigation implements NavigationStrategy {
        @Override
        public MoveIntention getNextMove(Robot robot, Map map) {
            Tile fromTile = map.getTile(robot.getPosition().getX(), robot.getPosition().getY());
            Vector2D target = robot.getTarget();
            int dx = Integer.compare(target.getX(), robot.getPosition().getX());
            Tile toTile = map.getTile(robot.getPosition().getX() + dx, robot.getPosition().getY());
            return new MoveIntention(fromTile, toTile, robot);
        }
    }

    /**
     * k=1 is the minimum allowed value.
     */
    @Test
    public void testKEqualsOneIsValid() {
        assertDoesNotThrow(() -> new ReservationKPolicy(1));
    }

    /**
     * k values greater than 1 are also valid.
     */
    @Test
    public void testKGreaterThanOneIsValid() {
        assertDoesNotThrow(() -> new ReservationKPolicy(5));
    }

    /**
     * k=0 must throw IllegalArgumentException.
     */
    @Test
    public void testKEqualsZeroThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ReservationKPolicy(0));
    }

    /**
     * Negative k must throw IllegalArgumentException.
     */
    @Test
    public void testNegativeKThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ReservationKPolicy(-3));
    }

    /**
     * getK() returns the value passed to the constructor.
     */
    @Test
    public void testGetK() {
        assertEquals(1, new ReservationKPolicy(1).getK());
        assertEquals(7, new ReservationKPolicy(7).getK());
    }

    /**
     * Null input returns an empty array without throwing.
     */
    @Test
    public void testNullInputReturnsEmpty() {
        assertEquals(0, policy.apply(testMap(), null).length);
    }

    /**
     * Empty input returns an empty array.
     */
    @Test
    public void testEmptyInputReturnsEmpty() {
        assertEquals(0, policy.apply(testMap(), new MoveIntention[0]).length);
    }

    /**
     * An array containing only nulls is handled gracefully.
     */
    @Test
    public void testAllNullIntentionsReturnsEmpty() {
        assertEquals(0, policy.apply(testMap(), new MoveIntention[]{ null, null }).length);
    }

    /**
     * Null entries are filtered out while valid intentions still proceed.
     */
    @Test
    public void testNullIntentionsAreIgnoredAroundValidMoves() {
        Robot r = makeRobot("R1");

        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{
                null,
                move(r, 0, 0, 1, 0),
                null
        });

        assertEquals(1, result.length);
        assertEquals(r.getId(), result[0].getRobot().getId());
        assertEquals(1, result[0].getToTile().getX());
    }

    /**
     * Intentions with a null robot are ignored.
     */
    @Test
    public void testNullRobotIntentionIsIgnored() {
        MoveIntention invalid = new MoveIntention(new Tile(0, 0), new Tile(1, 0), null);
        Robot validRobot = makeRobot("R1");

        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{
                invalid,
                move(validRobot, 2, 0, 3, 0)
        });

        assertEquals(1, result.length);
        assertEquals(validRobot.getId(), result[0].getRobot().getId());
        assertEquals(3, result[0].getToTile().getX());
    }

    /**
     * Missing tiles mean the move is treated as non-moving and passed through.
     */
    @Test
    public void testNullTilesPassThroughUnchanged() {
        Robot r = makeRobot("R1");
        MoveIntention missingFrom = new MoveIntention(null, new Tile(1, 0), r);
        MoveIntention missingTo = new MoveIntention(new Tile(1, 0), null, r);

        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{ missingFrom, missingTo });

        assertEquals(2, result.length);
        assertNull(result[0].getFromTile());
        assertEquals(1, result[0].getToTile().getX());
        assertEquals(1, result[1].getFromTile().getX());
        assertNull(result[1].getToTile());
    }

    /**
     * A single robot moving to an unreserved tile is approved as-is.
     */
    @Test
    public void testSingleMoveApproved() {
        Robot r = makeRobot("R1");
        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{ move(r, 0, 0, 1, 0) });
        assertEquals(1, result.length);
        assertEquals(new Tile(1, 0).getX(), result[0].getToTile().getX());
    }

    /**
     * A stay-in-place intention (from == to) always passes through unchanged.
     */
    @Test
    public void testStayIntentionPassesThrough() {
        Robot r = makeRobot("R1");
        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{ stay(r, 3, 3) });
        assertEquals(1, result.length);
        assertEquals(3, result[0].getFromTile().getX());
        assertEquals(3, result[0].getToTile().getX());
    }

    /**
     * Two robots targeting the same tile in the same tick:
     * the UUID-lowest (processed first after sort) reserves the tile;
     * the other is forced to wait.
     */
    @Test
    public void testSameDestinationSecondRobotWaits() {
        Robot r1 = makeRobot("R1");
        Robot r2 = makeRobot("R2");

        MoveIntention mi1 = move(r1, 0, 0, 1, 0);
        MoveIntention mi2 = move(r2, 2, 0, 1, 0);

        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{ mi1, mi2 });

        assertEquals(2, result.length, "Both intentions should be in the output (one as a wait)");

        // Find which robot was forced to wait (from == to)
        long waitCount = 0;
        for (MoveIntention mi : result) {
            if (mi.getFromTile().getX() == mi.getToTile().getX()
                    && mi.getFromTile().getY() == mi.getToTile().getY()) {
                waitCount++;
            }
        }
        assertEquals(1, waitCount, "Exactly one robot must be forced to wait");
    }

    /**
     * Head-on swaps across the same edge are blocked for the second processed robot.
     */
    @Test
    public void testReverseEdgeConflictForcesOneRobotToWait() {
        Robot r1 = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"), "R1", new Vector2D(0, 0));
        Robot r2 = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000002"), "R2", new Vector2D(1, 0));

        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{
                move(r2, 1, 0, 0, 0),
                move(r1, 0, 0, 1, 0)
        });

        assertMove(result, r1, 1, 0);
        assertMove(result, r2, 1, 0);
    }

    /**
     * Sorting by robot UUID makes approval deterministic regardless of input order.
     */
    @Test
    public void testLowerUuidWinsConflictingReservationRegardlessOfInputOrder() {
        Robot lower = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"), "lower", new Vector2D(0, 0));
        Robot higher = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000002"), "higher", new Vector2D(2, 0));

        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{
                move(higher, 2, 0, 1, 0),
                move(lower, 0, 0, 1, 0)
        });

        assertMove(result, lower, 1, 0);
        assertMove(result, higher, 2, 0);
    }

    /**
     * Reservations persist across ticks until the owning robot is processed and
     * releases them (e.g., via stay/no-move). Once released, another robot can acquire.
     */
    @Test
    public void testReservationsPersistUntilOwnerReleasesThenOtherRobotCanAcquire() {
        Robot r1 = makeRobot("R1");
        Robot r2 = makeRobot("R2");

        // Tick 1: r1 reserves tile (1,0)
        policy.apply(testMap(), new MoveIntention[]{ move(r1, 0, 0, 1, 0) });

        // Tick 2: r2 alone targets same tile and must wait because r1 still owns reservation.
        MoveIntention[] blocked = policy.apply(testMap(), new MoveIntention[]{ move(r2, 2, 0, 1, 0) });
        assertEquals(1, blocked.length);
        assertEquals(2, blocked[0].getToTile().getX(),
                "r2 should be forced to wait while r1 keeps the reservation");

        // Tick 3: process r1 as stay-in-place to release its reservations.
        policy.apply(testMap(), new MoveIntention[]{ stay(r1, 1, 0) });

        // Tick 4: r2 can now acquire and move to the previously reserved tile.
        MoveIntention[] released = policy.apply(testMap(), new MoveIntention[]{ move(r2, 2, 0, 1, 0) });
        assertEquals(1, released.length);
        assertEquals(1, released[0].getToTile().getX(),
                "r2 should move once the owner has released reservations");
    }

    /**
     * Three robots with different destination tiles are all approved.
     */
    @Test
    public void testThreeNonConflictingRobotsAllApproved() {
        Robot r1 = makeRobot("R1");
        Robot r2 = makeRobot("R2");
        Robot r3 = makeRobot("R3");

        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{
                move(r1, 0, 0, 1, 0),
                move(r2, 5, 0, 6, 0),
                move(r3, 0, 5, 0, 6)
        });

        assertEquals(3, result.length,
                "All three non-conflicting robots should be approved");
    }

    /**
     * A robot without a target only reserves its immediate destination tile.
     */
    @Test
    void robotWithoutTargetUsesSingleTileReservationWindow() {
        Map map = new Map(5, 1);
        ReservationKPolicy policy = new ReservationKPolicy(3);

        Robot robotA = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"), "robot-a", new Vector2D(0, 0));
        Robot robotB = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000002"), "robot-b", new Vector2D(3, 0));

        MoveIntention[] result = policy.apply(map, new MoveIntention[] {
                move(map, robotA, 0, 0, 1, 0),
                move(map, robotB, 3, 0, 2, 0)
        });

        assertMove(result, robotA, 1, 0);
        assertMove(result, robotB, 2, 0);
    }

    /**
     * A null map also falls back to reserving only the immediate destination tile.
     */
    @Test
    void nullMapStillApprovesIndependentImmediateMoves() {
        ReservationKPolicy policy = new ReservationKPolicy(3);
        Robot robotA = movingRobot("robot-a",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new Vector2D(0, 0),
                new Vector2D(4, 0));
        Robot robotB = movingRobot("robot-b",
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new Vector2D(3, 0),
                new Vector2D(0, 0));

        MoveIntention[] result = policy.apply(null, new MoveIntention[] {
                new MoveIntention(new Tile(0, 0), new Tile(1, 0), robotA),
                new MoveIntention(new Tile(3, 0), new Tile(2, 0), robotB)
        });

        assertMove(result, robotA, 1, 0);
        assertMove(result, robotB, 2, 0);
    }

    @Test
    void blocksMovesIntoFartherTilesReservedByTheBfsWindow() {
        Map map = new Map(5, 1);
        ReservationKPolicy policy = new ReservationKPolicy(3);

        Robot robotA = movingRobot("robot-a",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new Vector2D(0, 0),
                new Vector2D(4, 0));
        Robot robotB = movingRobot("robot-b",
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new Vector2D(4, 0),
                new Vector2D(0, 0));

        MoveIntention[] result = policy.apply(map, new MoveIntention[] {
                move(map, robotA, 0, 0, 1, 0),
                move(map, robotB, 4, 0, 3, 0)
        });

        // Robot A reserves 1,0 -> 2,0 -> 3,0, so robot B must wait at 4,0.
        assertMove(result, robotA, 1, 0);
        assertMove(result, robotB, 4, 0);
    }

    /**
     * If no path exists to the target, the reservation window stops at the immediate next tile.
     */
    @Test
    void unreachableTargetFallsBackToImmediateTileOnly() {
        Map map = new Map(3, 2);
        ReservationKPolicy policy = new ReservationKPolicy(3);

        Robot robotA = movingRobot("robot-a",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new Vector2D(0, 0),
                new Vector2D(5, 5));
        Robot robotB = new Robot(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "robot-b",
                new Vector2D(2, 0));

        MoveIntention[] result = policy.apply(map, new MoveIntention[] {
                move(map, robotA, 0, 0, 1, 0),
                move(map, robotB, 2, 0, 2, 1)
        });

        assertMove(result, robotA, 1, 0);
        assertMove(result, robotB, 2, 1);
    }

    /**
     * Releases consumed reservations as the robot progresses.
     */
    @Test
    void releasesConsumedReservationsAsTheRobotProgresses() {
        Map map = new Map(5, 1);
        ReservationKPolicy policy = new ReservationKPolicy(3);

        Robot robotA = movingRobot("robot-a",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new Vector2D(0, 0),
                new Vector2D(4, 0));

        // First tick: create reservations [1,0], [2,0], [3,0].
        policy.apply(map, new MoveIntention[] {
                move(map, robotA, 0, 0, 1, 0)
        });

        // Simulate the approved move being committed before the next tick.
        robotA.setPosition(new Vector2D(1, 0));

        Robot robotC = movingRobot("robot-c",
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                new Vector2D(0, 0),
                new Vector2D(1, 0));

        MoveIntention[] result = policy.apply(map, new MoveIntention[] {
                move(map, robotA, 1, 0, 2, 0),
                move(map, robotC, 0, 0, 1, 0)
        });

        // Tile 1,0 should have been released after robot A reached it.
        // Robot C only needs that one tile, so it can now reserve and enter it.
        assertMove(result, robotA, 2, 0);
        assertMove(result, robotC, 1, 0);
    }

    /**
     * Any non-moving tick releases the robot's outstanding reservations.
     */
    @Test
    void nullTileIntentionReleasesExistingReservations() {
        Map map = new Map(5, 1);
        ReservationKPolicy policy = new ReservationKPolicy(3);

        Robot robotA = movingRobot("robot-a",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new Vector2D(0, 0),
                new Vector2D(4, 0));
        Robot robotB = movingRobot("robot-b",
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new Vector2D(3, 0),
                new Vector2D(1, 0));

        policy.apply(map, new MoveIntention[] {
                move(map, robotA, 0, 0, 1, 0)
        });

        policy.apply(map, new MoveIntention[] {
                new MoveIntention(null, map.getTile(1, 0), robotA)
        });

        MoveIntention[] result = policy.apply(map, new MoveIntention[] {
                move(map, robotB, 3, 0, 2, 0)
        });

        assertMove(result, robotB, 2, 0);
    }

    /**
     * SimulationEngine applies reservation policy before collision resolution.
     */
    @Test
    void simulationEngineAppliesReservationPolicyBeforeCollisionResolution() {
        Map map = new Map(5, 1);

        Robot robotA = movingRobot("robot-a",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                new Vector2D(0, 0),
                new Vector2D(4, 0));
        robotA.setNav(new CorridorStepNavigation());

        Robot robotB = movingRobot("robot-b",
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                new Vector2D(4, 0),
                new Vector2D(0, 0));
        robotB.setNav(new CorridorStepNavigation());

        map.addEntity(robotA);
        map.addEntity(robotB);

        SimulationEngine engine = new SimulationEngine(
                map,
                new Robot[] { robotA, robotB },
                new Dispatcher(),
                new ReservationKPolicy(3)
        );

        engine.tick();

        // Without ReservationK being applied in the engine, robot B would move to 3,0.
        assertEquals(new Vector2D(1, 0), robotA.getPosition());
        assertEquals(new Vector2D(4, 0), robotB.getPosition());
    }
}
