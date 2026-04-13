package com.openrobotics.simulationcore;

import com.openrobotics.AppState;
import com.openrobotics.logging.Logger;
import com.openrobotics.logging.LoggerMode;
import com.openrobotics.map.Map;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CollisionManager}.
 */
public class CollisionManagerTest {

    private CollisionManager manager;

    /**
     * Seed AppState with a dummy SimulationEngine to satisfy logging code
     */
    private void seedAppState() {
        SimulationEngine dummyEngine = new SimulationEngine(
                new Map(1, 1),
                new Robot[0],
                new Dispatcher(),
                CoordinationPolicy.noOp()
        );

        AppState.setEngine(dummyEngine);
    }

    /**
     * Creates a fresh CollisionManager before every test.
     */
    @BeforeEach
    public void setUp() {
        manager = new CollisionManager();
        seedAppState();
        Logger.setMode(LoggerMode.NO_OP); // disable logging during tests
    }

    // Helpers
    /**
     * Creates a robot.
     * @param name the name of the robot
     * @return the robot
     */
    private static Robot makeRobot(String name) {
        return new Robot(name, new Vector2D(0, 0));
    }

    /**
     * Creates a move intention.
     * @param robot the robot
     * @param fromX the x coordinate of the from tile
     * @param fromY the y coordinate of the from tile
     * @param toX the x coordinate of the to tile
     * @param toY the y coordinate of the to tile
     * @return the move intention
     */
    private static MoveIntention move(Robot robot, int fromX, int fromY, int toX, int toY) {
        return new MoveIntention(new Tile(fromX, fromY), new Tile(toX, toY), robot);
    }

    /**
     * Creates a stay intention.
     * @param robot the robot
     * @param x the x coordinate of the tile
     * @param y the y coordinate of the tile
     * @return the stay intention
     */
    private static MoveIntention stay(Robot robot, int x, int y) {
        Tile tile = new Tile(x, y);
        return new MoveIntention(tile, tile, robot);
    }

    /**
     * A null intention is not legal.
     */
    @Test
    public void testNullIntentionIsIllegal() {
        assertFalse(manager.isLegalIntention(null));
    }

    /**
     * An intention with a null robot is illegal.
     */
    @Test
    public void testNullRobotIsIllegal() {
        MoveIntention mi = new MoveIntention(new Tile(0, 0), new Tile(1, 0), null);
        assertFalse(manager.isLegalIntention(mi));
    }

    /**
     * An intention with a null from-tile is illegal.
     */
    @Test
    public void testNullFromTileIsIllegal() {
        Robot robot = makeRobot("R1");
        MoveIntention mi = new MoveIntention(null, new Tile(1, 0), robot);
        assertFalse(manager.isLegalIntention(mi));
    }

    /**
     * An intention with a null to-tile is illegal.
     */
    @Test
    public void testNullToTileIsIllegal() {
        Robot robot = makeRobot("R1");
        MoveIntention mi = new MoveIntention(new Tile(0, 0), null, robot);
        assertFalse(manager.isLegalIntention(mi));
    }

    /**
     * A stay-in-place intention (distance 0) is legal.
     */
    @Test
    public void testStayIntentionIsLegal() {
        Robot robot = makeRobot("R1");
        assertTrue(manager.isLegalIntention(stay(robot, 3, 3)));
    }

    /**
     * A one-step horizontal move (distance 1) is legal.
     */
    @Test
    public void testOneStepMoveIsLegal() {
        Robot robot = makeRobot("R1");
        assertTrue(manager.isLegalIntention(move(robot, 0, 0, 1, 0)));
    }

    /**
     * A one-step vertical move (distance 1) is legal.
     */
    @Test
    public void testOneStepVerticalMoveIsLegal() {
        Robot robot = makeRobot("R1");
        assertTrue(manager.isLegalIntention(move(robot, 0, 0, 0, 1)));
    }

    /**
     * A two-step move (Manhattan distance 2) is illegal.
     */
    @Test
    public void testTwoStepMoveIsIllegal() {
        Robot robot = makeRobot("R1");
        assertFalse(manager.isLegalIntention(move(robot, 0, 0, 2, 0)),
                "Move of Manhattan distance 2 must be rejected");
    }

    /**
     * A diagonal jump (dx=1, dy=1, distance=2) is illegal.
     */
    @Test
    public void testDiagonalMoveIsIllegal() {
        Robot robot = makeRobot("R1");
        assertFalse(manager.isLegalIntention(move(robot, 0, 0, 1, 1)));
    }

    /**
     * Null input returns an empty array.
     */
    @Test
    public void testResolveNullReturnsEmpty() {
        assertEquals(0, manager.resolveConflicts(null).length);
    }

    /**
     * Empty input returns an empty array.
     */
    @Test
    public void testResolveEmptyReturnsEmpty() {
        assertEquals(0, manager.resolveConflicts(new MoveIntention[0]).length);
    }

    /**
     * An array of null entries is cleaned up; no approved intentions remain.
     */
    @Test
    public void testResolveAllNullIntentions() {
        MoveIntention[] input = { null, null };
        assertEquals(0, manager.resolveConflicts(input).length);
    }

    /**
     * Illegal intentions (distance > 1) are stripped out.
     */
    @Test
    public void testResolveStripsIllegalIntentions() {
        Robot robot = makeRobot("R1");
        MoveIntention illegal = move(robot, 0, 0, 5, 5); // distance >> 1
        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{ illegal });
        assertEquals(0, result.length);
    }

    /**
     * Null and illegal entries are discarded while legal entries still survive.
     */
    @Test
    public void testResolveKeepsLegalIntentionsAmidInvalidEntries() {
        Robot robot = makeRobot("R1");
        MoveIntention legal = move(robot, 0, 0, 1, 0);
        MoveIntention illegal = move(makeRobot("R2"), 0, 0, 3, 0);

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{ null, illegal, legal, null });

        assertEquals(1, result.length);
        assertSame(legal, result[0]);
    }

    /**
     * A single legal intention passes through unchanged.
     */
    @Test
    public void testResolveSingleLegalIntentionPassesThrough() {
        Robot robot = makeRobot("R1");
        MoveIntention mi = move(robot, 0, 0, 1, 0);
        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{ mi });
        assertEquals(1, result.length);
    }

    /**
     * Two robots targeting the same tile: only one (the UUID-lowest) is approved.
     */
    @Test
    public void testSameDestinationOnlyOneApproved() {
        Robot r1 = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "R1", new Vector2D(0, 0));
        Robot r2 = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "R2", new Vector2D(2, 0));

        // Both want tile (1,0)
        MoveIntention mi1 = move(r1, 0, 0, 1, 0);
        MoveIntention mi2 = move(r2, 2, 0, 1, 0);

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{ mi1, mi2 });
        assertEquals(1, result.length,
                "Only one robot should be approved when both target the same tile");
        // r1 has lexicographically smaller UUID string → wins
        assertSame(r1, result[0].getRobot());
    }

    /**
     * Three robots targeting the same tile: only the UUID-lowest is approved.
     */
    @Test
    public void testThreeRobotsSameDestinationOnlyOneWins() {
        Robot r1 = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "R1", new Vector2D(0, 0));
        Robot r2 = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "R2", new Vector2D(2, 0));
        Robot r3 = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "R3", new Vector2D(0, 2));

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{
                move(r1, 0, 0, 1, 0),
                move(r2, 2, 0, 1, 0),
                move(r3, 0, 2, 1, 0)
        });
        assertEquals(1, result.length);
        assertSame(r1, result[0].getRobot());
    }

    /**
     * Two robots swapping positions: none of them are approved.
     */
    @Test
    public void testSwapConflictNoneApproved() {
        Robot r1 = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "R1", new Vector2D(0, 0));
        Robot r2 = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "R2", new Vector2D(1, 0));

        // r1: (0,0) → (1,0); r2: (1,0) → (0,0)  — classic swap
        MoveIntention mi1 = move(r1, 0, 0, 1, 0);
        MoveIntention mi2 = move(r2, 1, 0, 0, 0);

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{ mi1, mi2 });
        assertEquals(0, result.length,
                "Both robots must be rejected in a swap conflict");
    }

    /**
     * Two robots with independent, non-conflicting moves are both approved.
     */
    @Test
    public void testNonConflictingMovesAllApproved() {
        Robot r1 = makeRobot("R1");
        Robot r2 = makeRobot("R2");

        MoveIntention mi1 = move(r1, 0, 0, 1, 0);
        MoveIntention mi2 = move(r2, 5, 5, 6, 5);

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{ mi1, mi2 });
        assertEquals(2, result.length,
                "Non-conflicting robots should both be approved");
    }

    /**
     * A follower cannot enter another robot's starting tile in the same tick on normal floor tiles.
     */
    @Test
    public void testSameDirectionFollowThroughIsBlocked() {
        Robot leader = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "leader", new Vector2D(1, 0));
        Robot follower = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "follower", new Vector2D(0, 0));

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{
                move(leader, 1, 0, 2, 0),
                move(follower, 0, 0, 1, 0)
        });

        assertEquals(1, result.length);
        assertSame(leader, result[0].getRobot());
    }

    /**
     * In a same-direction chain, only the front robot may advance into a free tile.
     */
    @Test
    public void testOnlyFrontRobotMovesInSameDirectionChain() {
        Robot front = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "front", new Vector2D(2, 0));
        Robot middle = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "middle", new Vector2D(1, 0));
        Robot rear = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "rear", new Vector2D(0, 0));

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{
                move(front, 2, 0, 3, 0),
                move(middle, 1, 0, 2, 0),
                move(rear, 0, 0, 1, 0)
        });

        assertEquals(1, result.length);
        assertSame(front, result[0].getRobot());
    }

    /**
     * Stay intentions from multiple robots are never in conflict and all pass through.
     */
    @Test
    public void testMultipleStayIntentionsAllApproved() {
        Robot r1 = makeRobot("R1");
        Robot r2 = makeRobot("R2");
        Robot r3 = makeRobot("R3");

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{
                stay(r1, 0, 0), stay(r2, 1, 1), stay(r3, 2, 2)
        });
        assertEquals(3, result.length);
    }

    /**
     * Duplicate intentions from the same robot: only the first is kept.
     */
    @Test
    public void testDuplicateRobotOnlyOneIntentionKept() {
        Robot robot = makeRobot("R1");
        MoveIntention mi1 = move(robot, 0, 0, 1, 0);
        MoveIntention mi2 = move(robot, 0, 0, 0, 1); // same robot, different direction

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{ mi1, mi2 });
        assertEquals(1, result.length,
                "Only one intention per robot must survive deduplication");
    }

    /**
     * Deduplication is based on the first legal intention; earlier illegal intentions do not block later legal ones.
     */
    @Test
    public void testFirstLegalIntentionWinsPerRobot() {
        Robot robot = makeRobot("R1");
        MoveIntention illegal = move(robot, 0, 0, 2, 0);
        MoveIntention legal1 = move(robot, 0, 0, 1, 0);
        MoveIntention legal2 = move(robot, 0, 0, 0, 1);

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{ illegal, legal1, legal2 });

        assertEquals(1, result.length);
        assertSame(legal1, result[0]);
    }

    /**
     * Approved intentions are returned in UUID order, not input order.
     */
    @Test
    public void testApprovedIntentionsAreSortedByRobotUuid() {
        Robot high = new Robot(UUID.fromString("00000000-0000-0000-0000-0000000000ff"),
                "high", new Vector2D(5, 5));
        Robot low = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "low", new Vector2D(0, 0));

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{
                move(high, 5, 5, 6, 5),
                move(low, 0, 0, 1, 0)
        });

        assertEquals(2, result.length);
        assertSame(low, result[0].getRobot());
        assertSame(high, result[1].getRobot());
    }

    /**
     * A stay intention and a moving intention to the same tile are treated as a same-destination conflict.
     */
    @Test
    public void testStayAndIncomingMoveConflictOnSameDestination() {
        Robot staying = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "stay", new Vector2D(1, 0));
        Robot moving = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "move", new Vector2D(0, 0));

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{
                stay(staying, 1, 0),
                move(moving, 0, 0, 1, 0)
        });

        assertEquals(1, result.length);
        assertSame(staying, result[0].getRobot());
    }

    /**
     * Dead-robot tiles reject incoming moves even on tiles that would otherwise allow overlap.
     */
    @Test
    public void testDeadRobotTileBlocksIncomingMoves() {
        Map map = new Map(3, 1);
        map.getTile(1, 0).setDeliveryStation(true);

        Robot dead = new Robot(UUID.fromString("00000000-0000-0000-0000-0000000000ff"),
                "dead", new Vector2D(1, 0));
        dead.setState(com.openrobotics.robot.RobotState.BATTER_DEAD);
        map.addEntity(dead);

        Robot moving = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "move", new Vector2D(0, 0));

        MoveIntention[] result = manager.resolveConflicts(map, new MoveIntention[]{
                move(moving, 0, 0, 1, 0)
        });

        assertEquals(0, result.length);
    }

    /**
     * Delivery stations still allow overlap when a robot is already on the tile.
     */
    @Test
    public void testDeliveryStationAllowsIncomingMoveToOccupiedTile() {
        Map map = new Map(3, 1);
        map.getTile(1, 0).setDeliveryStation(true);

        Robot staying = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "stay", new Vector2D(1, 0));
        Robot moving = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "move", new Vector2D(0, 0));

        MoveIntention[] result = manager.resolveConflicts(map, new MoveIntention[]{
                new MoveIntention(map.getTile(1, 0), map.getTile(1, 0), staying),
                new MoveIntention(map.getTile(0, 0), map.getTile(1, 0), moving)
        });

        assertEquals(2, result.length);
    }

    /**
     * A stay intention does not participate in swap conflicts because it is not an actual move.
     */
    @Test
    public void testStayIntentionDoesNotTriggerSwapBlocking() {
        Robot staying = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "stay", new Vector2D(0, 0));
        Robot moving = new Robot(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "move", new Vector2D(1, 0));

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{
                stay(staying, 0, 0),
                move(moving, 1, 0, 1, 1)
        });

        assertEquals(2, result.length);
    }

    /**
     * The approved set should contain each robot at most once.
     */
    @Test
    public void testApprovedContainsUniqueRobots() {
        Robot r1 = makeRobot("R1");
        Robot r2 = makeRobot("R2");

        MoveIntention[] result = manager.resolveConflicts(new MoveIntention[]{
                move(r1, 0, 0, 1, 0),
                move(r2, 5, 5, 6, 5)
        });

        Set<UUID> ids = Arrays.stream(result)
                .map(mi -> mi.getRobot().getId())
                .collect(Collectors.toSet());
        assertEquals(result.length, ids.size(),
                "Approved intentions must contain each robot at most once");
    }
}
