package com.openrobotics.robot.sensors;

import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.robot.Robot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProximitySensor}.
 *
 * <p>Verifies that the sensor scans the correct 3×3 area around the robot,
 * excludes the robot's own cell, handles map boundaries gracefully, and
 * reports the {@code toString()} label correctly.
 */
public class ProximitySensorTest {

    private static final int MAP_SIZE = 10;

    private Map map;
    private ProximitySensor sensor;

    /**
     * Creates a fresh 10×10 map and a new ProximitySensor before every test.
     */
    @BeforeEach
    public void setUp() {
        map = new Map(MAP_SIZE, MAP_SIZE);
        sensor = new ProximitySensor();
    }

    /**
     * A robot placed on an empty map should produce a scan with no detected entities.
     */
    @Test
    public void testScanEmptyMapNoEntities() {
        Robot robot = new Robot("R1", new Vector2D(5, 5));
        Sensor result = sensor.scan(robot, map);
        assertTrue(result.getDetectedEntities().isEmpty(),
                "Scan on empty map must return no entities");
    }

    /**
     * An obstacle immediately to the right of the robot should be detected.
     */
    @Test
    public void testDetectsAdjacentObstacle() {
        Robot robot    = new Robot("R1", new Vector2D(5, 5));
        Obstacle wall  = new Obstacle("Wall", new Vector2D(6, 5));
        map.addEntity(wall);

        Sensor result = sensor.scan(robot, map);
        assertTrue(result.getDetectedEntities().contains(wall));
    }

    /**
     * An obstacle diagonally adjacent (1 step diagonally) should also be detected.
     */
    @Test
    public void testDetectsDiagonalObstacle() {
        Robot robot   = new Robot("R1", new Vector2D(5, 5));
        Obstacle wall = new Obstacle("DiagWall", new Vector2D(6, 6));
        map.addEntity(wall);

        Sensor result = sensor.scan(robot, map);
        assertTrue(result.getDetectedEntities().contains(wall));
    }

    /**
     * All 8 cells around the robot (the full 3×3 minus the centre) should be scanned.
     * Place one entity in each surrounding cell and verify all 8 are returned.
     */
    @Test
    public void testDetectsAllEightNeighboringCells() {
        Robot robot = new Robot("R1", new Vector2D(5, 5));
        int[][] offsets = { {-1,-1},{0,-1},{1,-1},{-1,0},{1,0},{-1,1},{0,1},{1,1} };
        for (int[] off : offsets) {
            map.addEntity(new MapEntity("E" + off[0] + off[1],
                    new Vector2D(5 + off[0], 5 + off[1])));
        }
        Sensor result = sensor.scan(robot, map);
        assertEquals(8, result.getDetectedEntities().size(),
                "All 8 surrounding cells should be scanned");
    }

    /**
     * An entity placed exactly at the robot's own position should NOT be detected
     * because the sensor skips (dx=0, dy=0).
     */
    @Test
    public void testDoesNotDetectEntityAtRobotPosition() {
        Robot robot = new Robot("R1", new Vector2D(5, 5));
        map.addEntity(new MapEntity("SelfEntity", new Vector2D(5, 5)));

        Sensor result = sensor.scan(robot, map);
        assertTrue(result.getDetectedEntities().isEmpty(),
                "Entity at robot's own position must not be detected");
    }

    /**
     * An entity 2 tiles away should NOT be detected (range = 1).
     */
    @Test
    public void testDoesNotDetectOutOfRangeEntity() {
        Robot robot  = new Robot("R1", new Vector2D(5, 5));
        Obstacle far = new Obstacle("FarWall", new Vector2D(7, 5));
        map.addEntity(far);

        Sensor result = sensor.scan(robot, map);
        assertFalse(result.getDetectedEntities().contains(far),
                "Entity 2 tiles away must not be detected by proximity sensor (range=1)");
    }

    /**
     * A robot at the corner (0,0) should not throw even when the scan
     * attempts to check out-of-bounds positions.
     */
    @Test
    public void testScanAtMapCornerNoException() {
        Robot robot = new Robot("R1", new Vector2D(0, 0));
        assertDoesNotThrow(() -> sensor.scan(robot, map),
                "Scan at map corner must not throw an exception");
    }

    /**
     * A robot at the edge of the map should return only entities within the map boundary.
     */
    @Test
    public void testScanAtMapEdgeDetectsInBoundsEntities() {
        Robot robot   = new Robot("R1", new Vector2D(0, 0));
        Obstacle wall = new Obstacle("Corner", new Vector2D(1, 0));
        map.addEntity(wall);

        Sensor result = sensor.scan(robot, map);
        assertTrue(result.getDetectedEntities().contains(wall));
    }

    /**
     * A robot at the opposite corner should also scan safely and detect in-bounds neighbors.
     */
    @Test
    public void testScanAtOppositeCornerDetectsInBoundsEntities() {
        Robot robot   = new Robot("R1", new Vector2D(MAP_SIZE - 1, MAP_SIZE - 1));
        Obstacle wall = new Obstacle("Corner", new Vector2D(MAP_SIZE - 2, MAP_SIZE - 1));
        map.addEntity(wall);

        Sensor result = sensor.scan(robot, map);
        assertTrue(result.getDetectedEntities().contains(wall));
    }

    /**
     * toString() must return "PROXIMITY".
     */
    @Test
    public void testToString() {
        assertEquals("PROXIMITY", sensor.toString());
    }

    /**
     * Multiple entities at the same adjacent tile should all be returned.
     */
    @Test
    public void testMultipleEntitiesAtSameAdjacentTile() {
        Robot robot = new Robot("R1", new Vector2D(5, 5));
        Vector2D pos = new Vector2D(6, 5);
        map.addEntity(new MapEntity("A", pos));
        map.addEntity(new MapEntity("B", pos));

        Sensor result = sensor.scan(robot, map);
        List<MapEntity> detected = result.getDetectedEntities();

        long count = detected.stream()
                .filter(e -> e.getPosition().equals(pos))
                .count();
        assertEquals(2, count,
                "Both entities at the same adjacent tile must be detected");
    }

    /**
     * Non-obstacle entities in range are also included in the raw sensor results.
     */
    @Test
    public void testDetectsNonObstacleEntitiesToo() {
        Robot robot = new Robot("R1", new Vector2D(5, 5));
        MapEntity entity = new MapEntity("Box", new Vector2D(4, 5));
        map.addEntity(entity);

        Sensor result = sensor.scan(robot, map);

        assertTrue(result.getDetectedEntities().contains(entity));
    }

    /**
     * Entities on different neighboring tiles are all accumulated into one scan result.
     */
    @Test
    public void testDetectsEntitiesAcrossMultipleNeighborTiles() {
        Robot robot = new Robot("R1", new Vector2D(5, 5));
        MapEntity left = new MapEntity("Left", new Vector2D(4, 5));
        MapEntity up = new MapEntity("Up", new Vector2D(5, 4));
        MapEntity diag = new MapEntity("Diag", new Vector2D(6, 6));
        map.addEntity(left);
        map.addEntity(up);
        map.addEntity(diag);

        Sensor result = sensor.scan(robot, map);

        assertEquals(3, result.getDetectedEntities().size());
        assertTrue(result.getDetectedEntities().contains(left));
        assertTrue(result.getDetectedEntities().contains(up));
        assertTrue(result.getDetectedEntities().contains(diag));
    }
}
