package com.openrobotics.simulationcore;

import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MoveIntention}.
 *
 * Verifies construction, all three getters ({@code getRobot},
 * {@code getFromTile}, {@code getToTile}), and the convenience
 * {@code getRobotId()} delegate.
 */
public class MoveIntentionTest {

    /**
     * getRobot() must return the robot passed to the constructor.
     */
    @Test
    public void testGetRobotReturnsCorrectRobot() {
        Robot  robot = new Robot("R1", new Vector2D(0, 0));
        Tile   from  = new Tile(0, 0);
        Tile   to    = new Tile(1, 0);
        MoveIntention mi = new MoveIntention(from, to, robot);

        assertSame(robot, mi.getRobot());
    }

    /**
     * getFromTile() must return the "from" tile passed to the constructor.
     */
    @Test
    public void testGetFromTile() {
        Robot  robot = new Robot("R1", new Vector2D(0, 0));
        Tile   from  = new Tile(3, 3);
        Tile   to    = new Tile(4, 3);
        MoveIntention mi = new MoveIntention(from, to, robot);

        assertSame(from, mi.getFromTile());
    }

    /**
     * getToTile() must return the "to" tile passed to the constructor.
     */
    @Test
    public void testGetToTile() {
        Robot  robot = new Robot("R1", new Vector2D(0, 0));
        Tile   from  = new Tile(5, 5);
        Tile   to    = new Tile(5, 6);
        MoveIntention mi = new MoveIntention(from, to, robot);

        assertSame(to, mi.getToTile());
    }

    /**
     * getRobotId() must return the same UUID as the robot's own getMapid().
     */
    @Test
    public void testGetRobotIdMatchesRobotId() {
        Robot robot = new Robot("R2", new Vector2D(2, 2));
        MoveIntention mi = new MoveIntention(new Tile(2, 2), new Tile(3, 2), robot);

        assertEquals(robot.getId(), mi.getRobotId());
    }

    /**
     * getRobotId() should preserve an explicitly loaded robot UUID too.
     */
    @Test
    public void testGetRobotIdMatchesExplicitRobotUuid() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000321");
        Robot robot = new Robot(id, "R2", new Vector2D(2, 2));
        MoveIntention mi = new MoveIntention(new Tile(2, 2), new Tile(3, 2), robot);

        assertEquals(id, mi.getRobotId());
    }

    /**
     * A stay-in-place intention (from == to) is valid and both tile getters
     * return the same object.
     */
    @Test
    public void testStayIntention() {
        Robot robot = new Robot("R3", new Vector2D(7, 7));
        Tile  tile  = new Tile(7, 7);
        MoveIntention mi = new MoveIntention(tile, tile, robot);

        assertSame(mi.getFromTile(), mi.getToTile(),
                "Stay intention must have the same from and to tile");
    }

    /**
     * A MoveIntention constructed with null tiles should store those nulls
     * and not throw during construction.
     */
    @Test
    public void testNullTilesStoredWithoutException() {
        Robot robot = new Robot("R4", new Vector2D(0, 0));
        MoveIntention mi = assertDoesNotThrow(
                () -> new MoveIntention(null, null, robot));
        assertNull(mi.getFromTile());
        assertNull(mi.getToTile());
    }

    /**
     * A null robot is stored as-is by the constructor.
     */
    @Test
    public void testNullRobotStoredWithoutException() {
        MoveIntention mi = assertDoesNotThrow(
                () -> new MoveIntention(new Tile(0, 0), new Tile(1, 0), null));

        assertNull(mi.getRobot());
        assertEquals(0, mi.getFromTile().getX());
        assertEquals(1, mi.getToTile().getX());
    }

    /**
     * getRobotId() currently requires a non-null robot and should fail fast otherwise.
     */
    @Test
    public void testGetRobotIdThrowsWhenRobotIsNull() {
        MoveIntention mi = new MoveIntention(new Tile(0, 0), new Tile(1, 0), null);

        assertThrows(NullPointerException.class, mi::getRobotId);
    }
}
