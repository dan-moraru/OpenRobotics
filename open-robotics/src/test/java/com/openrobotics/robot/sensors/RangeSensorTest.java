package com.openrobotics.robot.sensors;

import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.environment.Rack;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.robot.Robot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RangeSensor}.
 *
 * Focuses on forward-arc detection, heading behavior, obstacle ray stopping,
 * entity pass-through, deduplication, and boundary safety.
 */
public class RangeSensorTest {

    private static final int MAP_SIZE = 15;

    private Map map;
    private RangeSensor sensor;

    private static class HeadingRobot extends Robot {
        private Vector2D previousPosition;

        private HeadingRobot(String name, Vector2D position) {
            super(name, position);
        }

        public void setPreviousPositionForTest(Vector2D previousPosition) {
            this.previousPosition = previousPosition;
        }

        @Override
        public Vector2D getPreviousPosition() {
            return previousPosition;
        }
    }

    @BeforeEach
    public void setUp() {
        map = new Map(MAP_SIZE, MAP_SIZE);
        sensor = new RangeSensor();
    }

    private HeadingRobot makeRobot(int x, int y) {
        return new HeadingRobot("R1", new Vector2D(x, y));
    }

    /**
     * An empty map should produce no detections.
     */
    @Test
    public void testScanEmptyMapNoEntities() {
        HeadingRobot robot = makeRobot(7, 7);

        Sensor result = sensor.scan(robot, map);

        assertTrue(result.getDetectedEntities().isEmpty());
    }

    /**
     * With no previous position, the fallback heading points east.
     */
    @Test
    public void testDefaultHeadingDetectsEntityInFront() {
        HeadingRobot robot = makeRobot(7, 7);
        Obstacle front = new Obstacle("Front", new Vector2D(9, 7));
        map.addEntity(front);

        Sensor result = sensor.scan(robot, map);

        assertTrue(result.getDetectedEntities().contains(front));
    }

    /**
     * The east-facing fallback arc should not include targets directly behind the robot.
     */
    @Test
    public void testDefaultHeadingDoesNotDetectEntityBehind() {
        HeadingRobot robot = makeRobot(7, 7);
        Obstacle behind = new Obstacle("Behind", new Vector2D(5, 7));
        map.addEntity(behind);

        Sensor result = sensor.scan(robot, map);

        assertFalse(result.getDetectedEntities().contains(behind));
    }

    /**
     * Heading is derived from previousPosition -> currentPosition.
     */
    @Test
    public void testDerivedHeadingDetectsEntityAheadOfMovement() {
        HeadingRobot robot = makeRobot(7, 7);
        robot.setPreviousPositionForTest(new Vector2D(7, 8)); // moved up, facing north
        Obstacle ahead = new Obstacle("Ahead", new Vector2D(7, 5));
        map.addEntity(ahead);

        Sensor result = sensor.scan(robot, map);

        assertTrue(result.getDetectedEntities().contains(ahead));
    }

    /**
     * When previousPosition equals currentPosition, heading falls back to east.
     */
    @Test
    public void testSamePreviousPositionFallsBackToEastHeading() {
        HeadingRobot robot = makeRobot(7, 7);
        robot.setPreviousPositionForTest(new Vector2D(7, 7));
        Obstacle east = new Obstacle("East", new Vector2D(10, 7));
        Obstacle west = new Obstacle("West", new Vector2D(4, 7));
        map.addEntity(east);
        map.addEntity(west);

        Sensor result = sensor.scan(robot, map);

        assertTrue(result.getDetectedEntities().contains(east));
        assertFalse(result.getDetectedEntities().contains(west));
    }

    /**
     * Rays pass through racks and stations, so obstacles behind them are still detected.
     */
    @Test
    public void testRaysPassThroughNonObstacleEntities() {
        HeadingRobot robot = makeRobot(7, 7);
        Rack rack = new Rack("Rack", new Vector2D(8, 7));
        ChargingStation station = new ChargingStation("Station", new Vector2D(9, 7));
        Obstacle wall = new Obstacle("Wall", new Vector2D(10, 7));
        map.addEntity(rack);
        map.addEntity(station);
        map.addEntity(wall);

        Sensor result = sensor.scan(robot, map);

        assertTrue(result.getDetectedEntities().contains(rack));
        assertTrue(result.getDetectedEntities().contains(station));
        assertTrue(result.getDetectedEntities().contains(wall));
    }

    /**
     * An obstacle stops a ray, so entities farther behind that obstacle on the same line are not detected.
     */
    @Test
    public void testObstacleStopsRayBeforeFartherEntity() {
        HeadingRobot robot = makeRobot(7, 7);
        Obstacle wall = new Obstacle("Wall", new Vector2D(9, 7));
        MapEntity behindWall = new MapEntity("BehindWall", new Vector2D(11, 7));
        map.addEntity(wall);
        map.addEntity(behindWall);

        Sensor result = sensor.scan(robot, map);

        assertTrue(result.getDetectedEntities().contains(wall));
        assertFalse(result.getDetectedEntities().contains(behindWall));
    }

    /**
     * The scanning robot itself must not be included in detections even if present on the map.
     */
    @Test
    public void testDoesNotDetectSelf() {
        HeadingRobot robot = makeRobot(7, 7);
        map.addEntity(robot);
        map.addEntity(new MapEntity("Other", new Vector2D(8, 7)));

        Sensor result = sensor.scan(robot, map);

        assertFalse(result.getDetectedEntities().contains(robot));
        assertEquals(1, result.getDetectedEntities().size());
    }

    /**
     * Multiple rays hitting the same entity should still report it only once.
     */
    @Test
    public void testDetectionsAreDeduplicated() {
        HeadingRobot robot = makeRobot(7, 7);
        Obstacle obstacle = new Obstacle("WideHit", new Vector2D(10, 7));
        map.addEntity(obstacle);

        Sensor result = sensor.scan(robot, map);
        List<MapEntity> detected = result.getDetectedEntities();

        assertEquals(1, detected.stream().filter(e -> e == obstacle).count());
    }

    /**
     * Targets at the maximum scan range should still be detectable.
     */
    @Test
    public void testDetectsObstacleAtMaximumRange() {
        HeadingRobot robot = makeRobot(7, 7);
        Obstacle obstacle = new Obstacle("MaxRange", new Vector2D(12, 7));
        map.addEntity(obstacle);

        Sensor result = sensor.scan(robot, map);

        assertTrue(result.getDetectedEntities().contains(obstacle));
    }

    /**
     * Scanning near map boundaries should not throw.
     */
    @Test
    public void testScanAtCornerNoException() {
        HeadingRobot robot = makeRobot(0, 0);

        assertDoesNotThrow(() -> sensor.scan(robot, map));
    }

    /**
     * toString() should return the configuration label.
     */
    @Test
    public void testToString() {
        assertEquals("RANGE", sensor.toString());
    }
}
