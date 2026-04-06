package com.openrobotics.robot.sensors;

import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Sensor}.
 *
 * <p>Covers construction with varying entity lists, {@code getDetectedEntities()},
 * and the {@code isObstacleDetectedAt()} query method.
 */
public class SensorTest {

    /**
     * A Sensor created with an empty list should report no detected entities.
     */
    @Test
    public void testEmptySensorHasNoEntities() {
        Sensor sensor = new Sensor(new ArrayList<>());
        assertTrue(sensor.getDetectedEntities().isEmpty());
    }

    /**
     * A Sensor created with entities should expose the same entities.
     */
    @Test
    public void testSensorStoresEntities() {
        MapEntity e1 = new MapEntity("E1", new Vector2D(1, 1));
        MapEntity e2 = new MapEntity("E2", new Vector2D(2, 2));
        Sensor sensor = new Sensor(List.of(e1, e2));

        List<MapEntity> detected = sensor.getDetectedEntities();
        assertEquals(2, detected.size());
        assertTrue(detected.contains(e1));
        assertTrue(detected.contains(e2));
    }

    /**
     * isObstacleDetectedAt() returns false when the entity list is empty.
     */
    @Test
    public void testNoObstacleWhenEmpty() {
        Sensor sensor = new Sensor(new ArrayList<>());
        assertFalse(sensor.isObstacleDetectedAt(new Vector2D(0, 0)));
    }

    /**
     * isObstacleDetectedAt() returns true when an Obstacle sits at the queried position.
     */
    @Test
    public void testObstacleDetectedAtCorrectPosition() {
        Vector2D pos = new Vector2D(3, 5);
        Obstacle obstacle = new Obstacle("Wall", pos);
        Sensor sensor = new Sensor(List.of(obstacle));

        assertTrue(sensor.isObstacleDetectedAt(pos));
    }

    /**
     * isObstacleDetectedAt() returns false for an Obstacle that is at a different position.
     */
    @Test
    public void testObstacleNotDetectedAtWrongPosition() {
        Obstacle obstacle = new Obstacle("Wall", new Vector2D(9, 9));
        Sensor sensor = new Sensor(List.of(obstacle));

        assertFalse(sensor.isObstacleDetectedAt(new Vector2D(1, 1)));
    }

    /**
     * A non-Obstacle entity at the queried position must NOT trigger
     * isObstacleDetectedAt() because it checks for Obstacle type by position.
     */
    @Test
    public void testNonObstacleEntityDoesNotCount() {
        Vector2D pos = new Vector2D(4, 4);
        MapEntity generic = new MapEntity("GenericEntity", pos);
        Sensor sensor = new Sensor(List.of(generic));

        boolean result = sensor.isObstacleDetectedAt(pos);
        assertFalse(result, "Non-obstacle entities at a position must not count as obstacles");
    }

    /**
     * An Obstacle far away and a non-Obstacle at the query position:
     * isObstacleDetectedAt() should return true only at the obstacle's position.
     */
    @Test
    public void testMultipleEntitiesOnlyObstaclePosition() {
        Vector2D obstaclePos = new Vector2D(7, 7);
        Vector2D otherPos    = new Vector2D(1, 1);
        Obstacle obstacle    = new Obstacle("Wall", obstaclePos);
        MapEntity other      = new MapEntity("Other", otherPos);

        Sensor sensor = new Sensor(List.of(obstacle, other));

        assertTrue(sensor.isObstacleDetectedAt(obstaclePos));
        assertFalse(sensor.isObstacleDetectedAt(new Vector2D(0, 0)));
    }

    /**
     * The list returned by getDetectedEntities() must be the same list each call.
     */
    @Test
    public void testGetDetectedEntitiesReturnsSameReference() {
        List<MapEntity> entities = new ArrayList<>();
        entities.add(new MapEntity("A", new Vector2D(0, 0)));
        Sensor sensor = new Sensor(entities);

        assertSame(sensor.getDetectedEntities(), sensor.getDetectedEntities());
    }

    /**
     * A Sensor reports the correct count when multiple entities are at distinct positions.
     */
    @Test
    public void testMultipleEntitiesCount() {
        List<MapEntity> entities = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            entities.add(new MapEntity("E" + i, new Vector2D(i, i)));
        }
        Sensor sensor = new Sensor(entities);
        assertEquals(5, sensor.getDetectedEntities().size());
    }

    /**
     * getObstaclePositions() should return only obstacle locations.
     */
    @Test
    public void testGetObstaclePositionsReturnsOnlyObstacleLocations() {
        Obstacle obstacleA = new Obstacle("A", new Vector2D(1, 1));
        Obstacle obstacleB = new Obstacle("B", new Vector2D(2, 2));
        MapEntity other = new MapEntity("Other", new Vector2D(3, 3));
        Sensor sensor = new Sensor(List.of(obstacleA, obstacleB, other));

        Set<Vector2D> positions = sensor.getObstaclePositions();

        assertEquals(Set.of(new Vector2D(1, 1), new Vector2D(2, 2)), positions);
    }

    /**
     * Duplicate obstacle positions should be collapsed in the returned set.
     */
    @Test
    public void testGetObstaclePositionsDeduplicatesByPosition() {
        Vector2D pos = new Vector2D(4, 4);
        Sensor sensor = new Sensor(List.of(
                new Obstacle("A", pos),
                new Obstacle("B", pos)
        ));

        Set<Vector2D> positions = sensor.getObstaclePositions();

        assertEquals(1, positions.size());
        assertTrue(positions.contains(pos));
    }

    /**
     * getObstaclePositions() should be empty when no obstacles were detected.
     */
    @Test
    public void testGetObstaclePositionsEmptyWhenNoObstacles() {
        Sensor sensor = new Sensor(List.of(
                new MapEntity("A", new Vector2D(0, 0)),
                new MapEntity("B", new Vector2D(1, 1))
        ));

        assertEquals(new HashSet<>(), sensor.getObstaclePositions());
    }

    /**
     * knownClearanceToward() returns maxDist when no obstacle lies in the scanned direction.
     */
    @Test
    public void testKnownClearanceTowardReturnsMaxDistWhenDirectionIsClear() {
        Sensor sensor = new Sensor(List.of(
                new Obstacle("Elsewhere", new Vector2D(5, 5))
        ));

        assertEquals(4, sensor.knownClearanceToward(new Vector2D(0, 0), new Vector2D(1, 0), 4));
    }

    /**
     * knownClearanceToward() stops just before the first obstacle in the direction of travel.
     */
    @Test
    public void testKnownClearanceTowardStopsBeforeFirstObstacle() {
        Sensor sensor = new Sensor(List.of(
                new Obstacle("Wall", new Vector2D(3, 0))
        ));

        assertEquals(2, sensor.knownClearanceToward(new Vector2D(0, 0), new Vector2D(1, 0), 5));
    }

    /**
     * An obstacle in the very next tile yields zero known clearance.
     */
    @Test
    public void testKnownClearanceTowardReturnsZeroForImmediateObstacle() {
        Sensor sensor = new Sensor(List.of(
                new Obstacle("Wall", new Vector2D(1, 0))
        ));

        assertEquals(0, sensor.knownClearanceToward(new Vector2D(0, 0), new Vector2D(1, 0), 5));
    }

    /**
     * Diagonal directions use the sign of both axes when checking for obstacles.
     */
    @Test
    public void testKnownClearanceTowardHandlesDiagonalDirection() {
        Sensor sensor = new Sensor(List.of(
                new Obstacle("DiagWall", new Vector2D(2, 2))
        ));

        assertEquals(1, sensor.knownClearanceToward(new Vector2D(0, 0), new Vector2D(1, 1), 5));
    }

    /**
     * Obstacles beyond maxDist should not reduce the reported known clearance.
     */
    @Test
    public void testKnownClearanceTowardIgnoresObstacleBeyondMaxDist() {
        Sensor sensor = new Sensor(List.of(
                new Obstacle("FarWall", new Vector2D(5, 0))
        ));

        assertEquals(3, sensor.knownClearanceToward(new Vector2D(0, 0), new Vector2D(1, 0), 3));
    }

    /**
     * When from and toward are the same tile, the implementation scans the origin repeatedly.
     */
    @Test
    public void testKnownClearanceTowardSameFromAndTowardUsesOrigin() {
        Sensor sensor = new Sensor(List.of(
                new Obstacle("OriginWall", new Vector2D(0, 0))
        ));

        assertEquals(0, sensor.knownClearanceToward(new Vector2D(0, 0), new Vector2D(0, 0), 4));
    }

    /**
     * Non-obstacle entities on the path do not reduce known clearance.
     */
    @Test
    public void testKnownClearanceTowardIgnoresNonObstacleEntities() {
        Sensor sensor = new Sensor(List.of(
                new MapEntity("Box", new Vector2D(1, 0)),
                new MapEntity("Station", new Vector2D(2, 0))
        ));

        assertEquals(4, sensor.knownClearanceToward(new Vector2D(0, 0), new Vector2D(1, 0), 4));
    }
}
