package com.openrobotics.simulationcore;

import com.openrobotics.task.Task;
import com.openrobotics.map.Map;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TrafficRulesPolicy}.
 *
 * Verifies construction (null safety for the intersection set), that
 * non-intersection moves are unaffected, that the first robot to claim an
 * intersection tile succeeds while subsequent robots are forced to wait,
 * and that stay-in-place intentions are always preserved.
 */
public class TrafficRulesPolicyTest {

    // Helpers
    /**
     * Creates a robot fixture at origin with generated UUID.
     */
    private static Robot makeRobot(String name) {
        return new Robot(name, new Vector2D(0, 0));
    }

    /**
     * Creates a move intention using standalone tiles (not map-owned tile instances).
     */
    private static MoveIntention move(Robot robot, int fromX, int fromY, int toX, int toY) {
        return new MoveIntention(new Tile(fromX, fromY), new Tile(toX, toY), robot);
    }

    /**
     * Creates a stay intention where source and destination are identical.
     */
    private static MoveIntention stay(Robot robot, int x, int y) {
        Tile t = new Tile(x, y);
        return new MoveIntention(t, t, robot);
    }

    /**
     * Builds an intersection tile set from flat coordinate pairs.
     */
    private static Set<Tile> intersectionSet(int... coords) {
        Set<Tile> set = new HashSet<>();
        for (int i = 0; i < coords.length - 1; i += 2) {
            set.add(new Tile(coords[i], coords[i + 1]));
        }
        return set;
    }

    /**
     * Creates a generic map fixture used by policy-only tests.
     */
    private static Map testMap() {
        return new Map(20, 20);
    }

    /**
     * Constructing with a null intersection set must not throw.
     */
    @Test
    public void testNullIntersectionSetNoException() {
        assertDoesNotThrow(() -> new TrafficRulesPolicy(null));
    }

    /**
     * Constructing with an empty set must not throw.
     */
    @Test
    public void testEmptyIntersectionSetNoException() {
        assertDoesNotThrow(() -> new TrafficRulesPolicy(new HashSet<>()));
    }

    /**
     * Null intention array returns empty output.
     */
    @Test
    public void testNullInputReturnsEmpty() {
        TrafficRulesPolicy policy = new TrafficRulesPolicy(new HashSet<>());
        assertEquals(0, policy.apply(testMap(), null).length);
    }

    /**
     * Empty intention array returns empty output.
     */
    @Test
    public void testEmptyInputReturnsEmpty() {
        TrafficRulesPolicy policy = new TrafficRulesPolicy(new HashSet<>());
        assertEquals(0, policy.apply(testMap(), new MoveIntention[0]).length);
    }

    /**
     * When there are no configured intersections, all moves pass through unchanged.
     */
    @Test
    public void testNoIntersectionsAllMovesApproved() {
        TrafficRulesPolicy policy = new TrafficRulesPolicy(new HashSet<>());
        Robot r1 = makeRobot("R1");
        Robot r2 = makeRobot("R2");

        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{
                move(r1, 0, 0, 1, 0),
                move(r2, 5, 5, 6, 5)
        });
        assertEquals(2, result.length,
                "All moves should pass through when no intersections are configured");
    }

    /**
     * A move targeting a non-intersection tile is unaffected even when other
     * intersection tiles exist.
     */
    @Test
    public void testNonIntersectionTileAlwaysApproved() {
        TrafficRulesPolicy policy = new TrafficRulesPolicy(intersectionSet(3, 3));
        Robot r = makeRobot("R1");
        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{ move(r, 0, 0, 1, 0) });

        assertEquals(1, result.length);
        assertEquals(1, result[0].getToTile().getX());
    }

    /**
     * A single robot moving into an intersection tile is approved (it claims it).
     */
    @Test
    public void testSingleRobotClainsIntersection() {
        TrafficRulesPolicy policy = new TrafficRulesPolicy(intersectionSet(5, 5));
        Robot r = makeRobot("R1");
        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{ move(r, 4, 5, 5, 5) });

        assertEquals(1, result.length);
        assertEquals(5, result[0].getToTile().getX());
        assertEquals(5, result[0].getToTile().getY());
    }

    /**
     * Two robots trying to enter the same intersection tile:
     * the first (UUID-lowest) wins; the second is forced to wait.
     */
    @Test
    public void testTwoRobotsContendingForIntersection() {
        TrafficRulesPolicy policy = new TrafficRulesPolicy(intersectionSet(5, 5));
        Robot r1 = makeRobot("R1");
        Robot r2 = makeRobot("R2");

        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{
                move(r1, 4, 5, 5, 5),
                move(r2, 6, 5, 5, 5)
        });

        assertEquals(2, result.length, "Both intentions should be in output (one as wait)");

        long waitCount = 0;
        for (MoveIntention mi : result) {
            if (mi.getFromTile().getX() == mi.getToTile().getX()
                    && mi.getFromTile().getY() == mi.getToTile().getY()) {
                waitCount++;
            }
        }
        assertEquals(1, waitCount,
                "Exactly one robot must be forced to wait at a contended intersection");
    }

    /**
     * Three robots all targeting the same intersection tile:
     * exactly one succeeds; the other two are forced to wait.
     */
    @Test
    public void testThreeRobotsContendingForIntersectionOnlyOneWins() {
        TrafficRulesPolicy policy = new TrafficRulesPolicy(intersectionSet(5, 5));
        Robot r1 = makeRobot("R1");
        Robot r2 = makeRobot("R2");
        Robot r3 = makeRobot("R3");

        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{
                move(r1, 4, 5, 5, 5),
                move(r2, 6, 5, 5, 5),
                move(r3, 5, 4, 5, 5)
        });

        assertEquals(3, result.length);

        long moveCount = 0;
        for (MoveIntention mi : result) {
            if (mi.getToTile().getX() == 5 && mi.getToTile().getY() == 5
                    && (mi.getFromTile().getX() != 5 || mi.getFromTile().getY() != 5)) {
                moveCount++;
            }
        }
        assertEquals(1, moveCount, "Exactly one robot must enter the intersection tile");
    }

    /**
     * A stay-in-place intention (from == to) at an intersection tile is not
     * considered an "actual move" and passes through without claiming the tile.
     */
    @Test
    public void testStayAtIntersectionPassesThrough() {
        TrafficRulesPolicy policy = new TrafficRulesPolicy(intersectionSet(5, 5));
        Robot r = makeRobot("R1");
        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{ stay(r, 5, 5) });

        assertEquals(1, result.length);
        assertEquals(5, result[0].getFromTile().getX());
        assertEquals(5, result[0].getToTile().getX());
    }

    /**
     * Even when an intersection is claimed, a robot moving to a different
     * (non-intersection) tile in the same tick is unaffected.
     */
    @Test
    public void testIntersectionClaimDoesNotBlockNonIntersectionMove() {
        TrafficRulesPolicy policy = new TrafficRulesPolicy(intersectionSet(5, 5));
        Robot r1 = makeRobot("R1");
        Robot r2 = makeRobot("R2");

        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{
                move(r1, 4, 5, 5, 5), // claims intersection (5,5)
                move(r2, 0, 0, 1, 0)  // non-intersection move
        });

        assertEquals(2, result.length);

        MoveIntention r2Result = null;
        for (MoveIntention mi : result) {
            if (mi.getRobot().equals(r2)) {
                r2Result = mi;
            }
        }
        assertNotNull(r2Result);
        assertEquals(1, r2Result.getToTile().getX(),
                "Non-intersection robot must not be affected by intersection claim");
    }

    /**
     * Mutating the set passed to the constructor should not affect the policy's
     * intersection configuration.
     */
    @Test
    public void testIntersectionSetDefensiveCopy() {
        Set<Tile> intersections = intersectionSet(5, 5);
        TrafficRulesPolicy policy = new TrafficRulesPolicy(intersections);

        // Remove the tile after construction — policy should still enforce it
        intersections.clear();

        Robot r1 = makeRobot("R1");
        Robot r2 = makeRobot("R2");

        MoveIntention[] result = policy.apply(testMap(), new MoveIntention[]{
                move(r1, 4, 5, 5, 5),
                move(r2, 6, 5, 5, 5)
        });

        long waitCount = 0;
        for (MoveIntention mi : result) {
            if (mi.getFromTile().getX() == mi.getToTile().getX()
                    && mi.getFromTile().getY() == mi.getToTile().getY()) {
                waitCount++;
            }
        }
        assertEquals(1, waitCount,
                "Policy must use a defensive copy — clearing the source set must have no effect");
    }

    /**
     * When a robot is inside the intersection, other robots attempting to enter
     * are forced to wait.
     */
    @Test
    public void testRobotInsideIntersectionKeepsExclusiveOwnership() {
        TrafficRulesPolicy policy = new TrafficRulesPolicy(intersectionSet(5, 5));
        Map map = testMap();
        Robot owner = makeRobot("Owner");
        Robot challenger = makeRobot("Challenger");

        policy.apply(map, new MoveIntention[]{ move(owner, 4, 5, 5, 5) });
        owner.setPosition(new Vector2D(5, 5));

        MoveIntention[] result = policy.apply(map, new MoveIntention[]{
                move(owner, 5, 5, 5, 5),
                move(challenger, 6, 5, 5, 5)
        });

        MoveIntention challengerResult = null;
        for (MoveIntention mi : result) {
            if (mi.getRobot().equals(challenger)) {
                challengerResult = mi;
                break;
            }
        }
        assertNotNull(challengerResult);
        assertEquals(challengerResult.getFromTile().getX(), challengerResult.getToTile().getX());
        assertEquals(challengerResult.getFromTile().getY(), challengerResult.getToTile().getY());
    }

    /**
     * Once the owner exits an intersection tile, another robot can enter on the next tick.
     */
    @Test
    public void testIntersectionOwnershipReleasedAfterOwnerExits() {
        TrafficRulesPolicy policy = new TrafficRulesPolicy(intersectionSet(5, 5));
        Map map = testMap();
        Robot owner = makeRobot("Owner");
        Robot follower = makeRobot("Follower");

        policy.apply(map, new MoveIntention[]{ move(owner, 4, 5, 5, 5) });
        owner.setPosition(new Vector2D(5, 5));

        MoveIntention[] blocked = policy.apply(map, new MoveIntention[]{
                move(owner, 5, 5, 6, 5),
                move(follower, 6, 6, 5, 5)
        });
        MoveIntention followerBlocked = null;
        for (MoveIntention mi : blocked) {
            if (mi.getRobot().equals(follower)) {
                followerBlocked = mi;
                break;
            }
        }
        assertNotNull(followerBlocked);
        assertEquals(followerBlocked.getFromTile().getX(), followerBlocked.getToTile().getX());
        assertEquals(followerBlocked.getFromTile().getY(), followerBlocked.getToTile().getY());

        owner.setPosition(new Vector2D(6, 5));
        MoveIntention[] released = policy.apply(map, new MoveIntention[]{
                move(follower, 6, 6, 5, 5)
        });
        assertEquals(1, released.length);
        assertEquals(5, released[0].getToTile().getX());
        assertEquals(5, released[0].getToTile().getY());
    }

    /**
     * Longer stop time gets intersection priority when multiple robots contend for the same tile.
     */
    @Test
    void givesIntersectionPriorityToRobotWithLongerStopTime() {
        Map map = new Map(3, 3);
        TrafficRulesPolicy policy = new TrafficRulesPolicy(Set.of(map.getTile(1, 1)));

        Robot shortWait = unloadedRobot("short-wait",
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                new Vector2D(0, 1),
                new Vector2D(2, 1),
                1);
        Robot longWait = unloadedRobot("long-wait",
                UUID.fromString("00000000-0000-0000-0000-000000000012"),
                new Vector2D(1, 0),
                new Vector2D(1, 2),
                5);

        MoveIntention[] result = policy.apply(map, new MoveIntention[] {
                move(map, shortWait, 0, 1, 1, 1),
                move(map, longWait, 1, 0, 1, 1)
        });

        assertMove(result, shortWait, 0, 1);
        assertMove(result, longWait, 1, 1);
    }

    /**
     * When stop time ties, loaded robots (carrying payload) receive intersection priority.
     */
    @Test
    void givesIntersectionPriorityToLoadedRobotWhenStopTimeIsTied() {
        Map map = new Map(3, 3);
        TrafficRulesPolicy policy = new TrafficRulesPolicy(Set.of(map.getTile(1, 1)));

        Robot unloaded = unloadedRobot("unloaded",
                UUID.fromString("00000000-0000-0000-0000-000000000021"),
                new Vector2D(0, 1),
                new Vector2D(2, 1),
                3);
        Robot loaded = loadedRobot("loaded",
                UUID.fromString("00000000-0000-0000-0000-000000000022"),
                new Vector2D(1, 0),
                new Vector2D(1, 2),
                3);

        MoveIntention[] result = policy.apply(map, new MoveIntention[] {
                move(map, unloaded, 0, 1, 1, 1),
                move(map, loaded, 1, 0, 1, 1)
        });

        assertMove(result, unloaded, 0, 1);
        assertMove(result, loaded, 1, 1);
    }

    /**
     * Once a robot acquires an intersection, it remains exclusive until that robot exits.
     */
    @Test
    void keepsIntersectionExclusiveUntilTheActiveRobotLeaves() {
        Map map = new Map(3, 3);
        TrafficRulesPolicy policy = new TrafficRulesPolicy(Set.of(map.getTile(1, 1)));

        Robot queuedRobot = unloadedRobot("queued",
                UUID.fromString("00000000-0000-0000-0000-000000000041"),
                new Vector2D(0, 1),
                new Vector2D(2, 1),
                1);
        Robot activeRobot = unloadedRobot("active",
                UUID.fromString("00000000-0000-0000-0000-000000000042"),
                new Vector2D(1, 0),
                new Vector2D(1, 2),
                4);

        MoveIntention[] tickOne = policy.apply(map, new MoveIntention[] {
                move(map, queuedRobot, 0, 1, 1, 1),
                move(map, activeRobot, 1, 0, 1, 1)
        });
        assertMove(tickOne, queuedRobot, 0, 1);
        assertMove(tickOne, activeRobot, 1, 1);

        activeRobot.setPosition(new Vector2D(1, 1));

        MoveIntention[] tickTwo = policy.apply(map, new MoveIntention[] {
                move(map, queuedRobot, 0, 1, 1, 1),
                move(map, activeRobot, 1, 1, 1, 2)
        });
        assertMove(tickTwo, queuedRobot, 0, 1);
        assertMove(tickTwo, activeRobot, 1, 2);

        activeRobot.setPosition(new Vector2D(1, 2));

        MoveIntention[] tickThree = policy.apply(map, new MoveIntention[] {
                move(map, queuedRobot, 0, 1, 1, 1),
                stay(map, activeRobot, 1, 2)
        });
        assertMove(tickThree, queuedRobot, 1, 1);
    }

    /**
     * Creates an unloaded moving robot whose task target is also used as pickup target.
     */
    private static Robot unloadedRobot(String name, UUID id, Vector2D start, Vector2D pickupTarget, int stuckTicks) {
        Robot robot = new Robot(id, name, start);
        robot.setCurrentTask(new Task(id.hashCode(), pickupTarget, pickupTarget, 1));
        robot.setState(RobotState.MOVING);
        robot.setStuckTicks(stuckTicks);
        return robot;
    }

    /**
     * Creates a loaded moving robot with dropoff target and configured stuck-tick history.
     */
    private static Robot loadedRobot(String name, UUID id, Vector2D start, Vector2D dropoffTarget, int stuckTicks) {
        Robot robot = new Robot(id, name, start);
        robot.setCurrentTask(new Task(id.hashCode(), start, dropoffTarget, 1));
        robot.setHasPickedUp(true);
        robot.setState(RobotState.MOVING);
        robot.setStuckTicks(stuckTicks);
        return robot;
    }

    /**
     * Creates a map-backed move intention using map tile references.
     */
    private static MoveIntention move(Map map, Robot robot, int fromX, int fromY, int toX, int toY) {
        return new MoveIntention(map.getTile(fromX, fromY), map.getTile(toX, toY), robot);
    }

    /**
     * Creates a map-backed stay intention for the given tile.
     */
    private static MoveIntention stay(Map map, Robot robot, int x, int y) {
        Tile tile = map.getTile(x, y);
        return new MoveIntention(tile, tile, robot);
    }

    /**
     * Asserts that a robot's resulting move intention matches expected destination coordinates.
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
}
