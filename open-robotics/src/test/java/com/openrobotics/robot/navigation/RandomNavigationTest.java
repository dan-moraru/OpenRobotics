package com.openrobotics.robot.navigation;

import com.openrobotics.map.Map;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.robot.Robot;
import com.openrobotics.simulationcore.MoveIntention;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RandomNavigation}.
 *
 * <p>Focuses on validity guarantees rather than exact random choices.
 */
public class RandomNavigationTest {

    /** Shared map fixture reinitialized before each test. */
    private Map map;
    /** Strategy under test. */
    private RandomNavigation strategy;

    /**
     * Initializes a fresh open map and navigation strategy for each test case.
     */
    @BeforeEach
    public void setUp() {
        map = new Map(10, 10);
        strategy = new RandomNavigation();
    }

    /**
     * Creates a robot fixture at a specific tile.
     */
    private Robot makeRobot(int x, int y) {
        return new Robot("RandomBot", new Vector2D(x, y));
    }

    /**
     * On an open map, the chosen move must be one of the valid neighboring tiles.
     */
    @Test
    public void testOpenMapMoveIsOneOfValidNeighbors() {
        Robot robot = makeRobot(5, 5);

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(5, 5), move.getFromTile().getPosition());
        assertTrue(Set.of(
                new Vector2D(5, 4),
                new Vector2D(5, 6),
                new Vector2D(4, 5),
                new Vector2D(6, 5)
        ).contains(move.getToTile().getPosition()));
    }

    /**
     * At a corner, the chosen move must remain inside the map.
     */
    @Test
    public void testCornerMoveStaysInBounds() {
        Robot robot = makeRobot(0, 0);

        MoveIntention move = strategy.getNextMove(robot, map);

        assertTrue(Set.of(
                new Vector2D(1, 0),
                new Vector2D(0, 1)
        ).contains(move.getToTile().getPosition()));
    }

    /**
     * If only one move is valid, that move must always be selected.
     */
    @Test
    public void testSingleAvailableNeighborIsChosen() {
        Robot robot = makeRobot(5, 5);
        map.getTile(5, 4).setOccupied(true);
        map.getTile(5, 6).setOccupied(true);
        map.getTile(4, 5).setOccupied(true);

        MoveIntention move = strategy.getNextMove(robot, map);

        assertEquals(new Vector2D(6, 5), move.getToTile().getPosition());
    }

    /**
     * Occupied tiles should be ignored when choosing a move.
     */
    @Test
    public void testOccupiedTilesAreNotChosen() {
        Robot robot = makeRobot(5, 5);
        map.getTile(6, 5).setOccupied(true);

        for (int i = 0; i < 20; i++) {
            MoveIntention move = strategy.getNextMove(robot, map);
            assertNotEquals(new Vector2D(6, 5), move.getToTile().getPosition());
        }
    }

    /**
     * When every neighboring tile is invalid, the strategy should return a wait intention.
     */
    @Test
    public void testNoValidMovesReturnsStayIntention() {
        Robot robot = makeRobot(5, 5);
        map.getTile(5, 4).setOccupied(true);
        map.getTile(5, 6).setOccupied(true);
        map.getTile(4, 5).setOccupied(true);
        map.getTile(6, 5).setOccupied(true);

        MoveIntention move = strategy.getNextMove(robot, map);

        assertSame(move.getFromTile(), move.getToTile());
        assertEquals(new Vector2D(5, 5), move.getToTile().getPosition());
    }

    /**
     * Obstacle entities alone do not affect RandomNavigation because it relies on isValidMove(tile occupancy).
     */
    @Test
    public void testObstacleEntitiesDoNotBlockRandomNavigationByThemselves() {
        Robot robot = makeRobot(5, 5);
        map.addEntity(new Obstacle("Wall", new Vector2D(6, 5)));

        boolean choseObstacleTileAtLeastOnce = false;
        for (int i = 0; i < 50; i++) {
            MoveIntention move = strategy.getNextMove(robot, map);
            if (move.getToTile().getPosition().equals(new Vector2D(6, 5))) {
                choseObstacleTileAtLeastOnce = true;
                break;
            }
        }

        assertTrue(choseObstacleTileAtLeastOnce,
                "RandomNavigation currently uses isValidMove(), so obstacle entities alone do not block moves");
    }

    /**
     * toString() should expose the configuration label used by save/load code.
     */
    @Test
    public void testToString() {
        assertEquals("RANDOM", strategy.toString());
    }
}
