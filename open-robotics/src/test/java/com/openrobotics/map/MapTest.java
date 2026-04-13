package com.openrobotics.map;

import com.openrobotics.map.entities.environment.Obstacle;
import com.openrobotics.map.entities.station.ChargingStation;
import com.openrobotics.map.entities.station.DeliveryStation;
import com.openrobotics.robot.Robot;
import com.openrobotics.robot.RobotState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Map}.
 *
 * Covers grid construction, tile access, move validity, entity management,
 * traversability logic, neighbour enumeration, and charging-station lookup.
 */
public class MapTest {

    /** A 10×10 map reused across most tests. */
    private Map map;

    /**
     * Creates a fresh 10×10 map before each test.
     */
    @BeforeEach
    public void setUp() {
        map = new Map(10, 10);
    }

    /**
     * Width and height should match the values given to the constructor.
     */
    @Test
    public void testDimensions() {
        assertEquals(10, map.getWidth());
        assertEquals(10, map.getHeight());
    }

    /**
     * A non-square map should store width and height independently.
     */
    @Test
    public void testNonSquareDimensions() {
        Map rect = new Map(5, 15);
        assertEquals(5, rect.getWidth());
        assertEquals(15, rect.getHeight());
    }

    /**
     * Width and height must both be positive.
     */
    @Test
    public void testConstructorRejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new Map(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new Map(10, 0));
        assertThrows(IllegalArgumentException.class, () -> new Map(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new Map(10, -1));
    }

    /**
     * Every tile in the grid should be initialised (non-null) and unoccupied.
     */
    @Test
    public void testAllTilesInitialisedAndUnoccupied() {
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                Tile tile = map.getTile(x, y);
                assertNotNull(tile, "Tile at (" + x + "," + y + ") should not be null");
                assertFalse(tile.isOccupied(), "Tile at (" + x + "," + y + ") should start unoccupied");
            }
        }
    }

    /**
     * Tile coordinates stored in the grid must match their (x, y) lookup key.
     */
    @Test
    public void testTileCoordinatesMatchLookup() {
        Tile t = map.getTile(4, 7);
        assertEquals(4, t.getX());
        assertEquals(7, t.getY());
    }

    /**
     * getTile() on a valid corner returns the correct tile.
     */
    @Test
    public void testGetTileCorner() {
        assertNotNull(map.getTile(0, 0));
        assertNotNull(map.getTile(9, 9));
    }

    /**
     * Repeated getTile() calls for the same coordinates should return the same Tile instance.
     */
    @Test
    public void testGetTileReturnsSameInstanceForSameCoordinates() {
        assertSame(map.getTile(4, 7), map.getTile(4, 7));
    }

    /**
     * getTile() outside the grid should return null.
     */
    @Test
    public void testGetTileOutOfBoundsReturnsNull() {
        assertNull(map.getTile(-1, 0),  "Negative x should return null");
        assertNull(map.getTile(0, -1),  "Negative y should return null");
        assertNull(map.getTile(10, 0),  "x == width should return null");
        assertNull(map.getTile(0, 10),  "y == height should return null");
        assertNull(map.getTile(99, 99), "Far out-of-bounds should return null");
    }

    /**
     * An in-bounds, unoccupied tile is a valid move target.
     */
    @Test
    public void testValidMoveInBoundsUnoccupied() {
        assertTrue(map.isValidMove(5, 5));
    }

    /**
     * An occupied tile is not a valid move target.
     */
    @Test
    public void testInvalidMoveOccupiedTile() {
        map.getTile(3, 3).setOccupied(true);
        assertFalse(map.isValidMove(3, 3));
    }

    /**
     * A tile containing a dead robot is not a valid move target.
     */
    @Test
    public void testInvalidMoveDeadRobotTile() {
        Robot deadRobot = new Robot("Dead", new Vector2D(3, 3));
        deadRobot.setState(RobotState.BATTER_DEAD);
        map.addEntity(deadRobot);

        assertFalse(map.isValidMove(3, 3));
    }

    /**
     * Out-of-bounds coordinates are never valid moves.
     */
    @Test
    public void testInvalidMoveOutOfBounds() {
        assertFalse(map.isValidMove(-1, 0));
        assertFalse(map.isValidMove(10, 5));
    }

    /**
     * A fresh map should have an empty entity list.
     */
    @Test
    public void testNoEntitiesInitially() {
        assertTrue(map.getEntities().isEmpty());
    }

    /**
     * addEntity() should make the entity visible via getEntities().
     */
    @Test
    public void testAddEntityAppearsInGetEntities() {
        MapEntity entity = new MapEntity("E1", new Vector2D(1, 1));
        map.addEntity(entity);
        assertTrue(map.getEntities().contains(entity));
    }

    /**
     * Adding a delivery station marks its tile as overlap-allowed.
     */
    @Test
    public void testAddDeliveryStationMarksTile() {
        map.addEntity(new DeliveryStation("DS1", new Vector2D(2, 2)));

        assertTrue(map.getTile(2, 2).isDeliveryStation());
        assertTrue(map.getTile(2, 2).allowsRobotOverlap());
    }

    /**
     * Adding a charging station marks its tile as overlap-allowed.
     */
    @Test
    public void testAddChargingStationMarksTile() {
        map.addEntity(new ChargingStation("CS1", new Vector2D(3, 3)));

        assertTrue(map.getTile(3, 3).isChargingStation());
        assertTrue(map.getTile(3, 3).allowsRobotOverlap());
    }

    /**
     * removeEntity() should return true for an entity that was present.
     */
    @Test
    public void testRemoveEntityPresentReturnsTrue() {
        MapEntity entity = new MapEntity("E1", new Vector2D(1, 1));
        map.addEntity(entity);

        assertTrue(map.removeEntity(entity));
        assertFalse(map.getEntities().contains(entity));
    }

    /**
     * Removing the last delivery station clears the delivery-tile marker.
     */
    @Test
    public void testRemoveDeliveryStationClearsTileMarker() {
        DeliveryStation station = new DeliveryStation("DS1", new Vector2D(2, 2));
        map.addEntity(station);

        assertTrue(map.removeEntity(station));
        assertFalse(map.getTile(2, 2).isDeliveryStation());
        assertFalse(map.getTile(2, 2).allowsRobotOverlap());
    }

    /**
     * Removing the last charging station clears the charging-tile marker.
     */
    @Test
    public void testRemoveChargingStationClearsTileMarker() {
        ChargingStation station = new ChargingStation("CS1", new Vector2D(3, 3));
        map.addEntity(station);

        assertTrue(map.removeEntity(station));
        assertFalse(map.getTile(3, 3).isChargingStation());
        assertFalse(map.getTile(3, 3).allowsRobotOverlap());
    }

    /**
     * removeEntity() should return false for an entity that was never added.
     */
    @Test
    public void testRemoveEntityAbsentReturnsFalse() {
        assertFalse(map.removeEntity(new MapEntity("Ghost", new Vector2D(9, 9))));
    }

    /**
     * getEntities() should return an unmodifiable view.
     */
    @Test
    public void testGetEntitiesReturnsUnmodifiableList() {
        assertThrows(UnsupportedOperationException.class,
                () -> map.getEntities().add(new MapEntity("E1", new Vector2D(1, 1))));
    }

    /**
     * getEntitiesAt() returns only entities at the queried position.
     */
    @Test
    public void testGetEntitiesAtCorrectPosition() {
        Vector2D pos = new Vector2D(3, 4);
        MapEntity e1 = new MapEntity("A", pos);
        MapEntity e2 = new MapEntity("B", new Vector2D(5, 5));
        map.addEntity(e1);
        map.addEntity(e2);

        List<MapEntity> at34 = map.getEntitiesAt(pos);
        assertEquals(1, at34.size());
        assertTrue(at34.contains(e1));
        assertFalse(at34.contains(e2));
    }

    /**
     * getEntitiesAt() returns an empty list when no entity occupies that tile.
     */
    @Test
    public void testGetEntitiesAtEmptyPosition() {
        assertTrue(map.getEntitiesAt(new Vector2D(7, 7)).isEmpty());
    }

    /**
     * Mutating the list returned by getEntitiesAt() must not affect the map's stored entities.
     */
    @Test
    public void testGetEntitiesAtReturnsDefensiveList() {
        Vector2D pos = new Vector2D(2, 2);
        MapEntity entity = new MapEntity("X", pos);
        map.addEntity(entity);

        List<MapEntity> entitiesAt = map.getEntitiesAt(pos);
        entitiesAt.clear();

        assertEquals(1, map.getEntitiesAt(pos).size());
        assertTrue(map.getEntitiesAt(pos).contains(entity));
    }

    /**
     * Multiple entities at the same position are all returned by getEntitiesAt().
     */
    @Test
    public void testMultipleEntitiesAtSamePosition() {
        Vector2D pos = new Vector2D(2, 2);
        map.addEntity(new MapEntity("X", pos));
        map.addEntity(new MapEntity("Y", pos));
        assertEquals(2, map.getEntitiesAt(pos).size());
    }

    /**
     * An empty, in-bounds tile is traversable.
     */
    @Test
    public void testEmptyTileIsTraversable() {
        assertTrue(map.isTraversable(new Vector2D(5, 5)));
    }

    /**
     * Dead robots act as hard blockers for traversability checks.
     */
    @Test
    public void testDeadRobotTileIsNotTraversable() {
        Robot deadRobot = new Robot("Dead", new Vector2D(4, 4));
        deadRobot.setState(RobotState.BATTER_DEAD);
        map.addEntity(deadRobot);

        assertFalse(map.isTraversable(new Vector2D(4, 4)));
    }

    /**
     * Out-of-bounds positions are never traversable.
     */
    @Test
    public void testOutOfBoundsNotTraversable() {
        assertFalse(map.isTraversable(new Vector2D(-1, 0)));
        assertFalse(map.isTraversable(new Vector2D(0, 10)));
    }

    /**
     * A tile occupied by an {@link Obstacle} entity is not traversable.
     */
    @Test
    public void testObstacleBlocksTraversable() {
        Vector2D pos = new Vector2D(4, 4);
        map.addEntity(new Obstacle("Wall", pos));
        assertFalse(map.isTraversable(pos));
    }

    /**
     * Tile occupancy alone does not affect traversability; only obstacles do.
     */
    @Test
    public void testOccupiedTileWithoutObstacleIsStillTraversable() {
        map.getTile(4, 4).setOccupied(true);
        assertTrue(map.isTraversable(new Vector2D(4, 4)));
    }

    /**
     * A non-obstacle entity (e.g. a charging station) does NOT block traversability.
     */
    @Test
    public void testNonObstacleEntityDoesNotBlockTraversable() {
        Vector2D pos = new Vector2D(6, 6);
        map.addEntity(new ChargingStation("Charger", pos));
        assertTrue(map.isTraversable(pos));
    }

    /**
     * A centre tile on a 10×10 grid with no obstacles has four neighbours.
     */
    @Test
    public void testCentreTileHasFourNeighbors() {
        List<Vector2D> neighbours = map.getNeighbors(new Vector2D(5, 5));
        assertEquals(4, neighbours.size());
    }

    /**
     * Neighbours should be returned in Direction enum order for determinism.
     */
    @Test
    public void testNeighborsReturnedInDirectionOrder() {
        List<Vector2D> neighbours = map.getNeighbors(new Vector2D(5, 5));
        assertEquals(List.of(
                new Vector2D(5, 4),
                new Vector2D(5, 6),
                new Vector2D(4, 5),
                new Vector2D(6, 5)
        ), neighbours);
    }

    /**
     * The corner tile (0,0) has exactly two traversable neighbours.
     */
    @Test
    public void testCornerTileHasTwoNeighbors() {
        List<Vector2D> neighbours = map.getNeighbors(new Vector2D(0, 0));
        assertEquals(2, neighbours.size());
    }

    /**
     * An obstacle adjacent to the query position is excluded from neighbours.
     */
    @Test
    public void testObstacleExcludedFromNeighbors() {
        Vector2D centre = new Vector2D(5, 5);
        Vector2D up = new Vector2D(5, 4); // Direction.UP has dy = -1
        map.addEntity(new Obstacle("Block", up));

        List<Vector2D> neighbours = map.getNeighbors(centre);
        assertFalse(neighbours.contains(up), "Obstacle position must not appear as a neighbour");
        assertEquals(3, neighbours.size());
    }

    /**
     * Non-obstacle entities remain valid neighbors because only obstacles block traversal.
     */
    @Test
    public void testNonObstacleEntityIncludedInNeighbors() {
        Vector2D centre = new Vector2D(5, 5);
        Vector2D right = new Vector2D(6, 5);
        map.addEntity(new ChargingStation("Charger", right));

        List<Vector2D> neighbours = map.getNeighbors(centre);

        assertTrue(neighbours.contains(right));
    }

    /**
     * Returns null when no charging station exists on the map.
     */
    @Test
    public void testFindNearestChargingStationNoStations() {
        assertNull(map.findNearestChargingStation(new Vector2D(0, 0)));
    }

    /**
     * Returns the position of the single charging station on the map.
     */
    @Test
    public void testFindNearestChargingStationSingle() {
        Vector2D stationPos = new Vector2D(7, 7);
        map.addEntity(new ChargingStation("CS1", stationPos));
        assertEquals(stationPos, map.findNearestChargingStation(new Vector2D(0, 0)));
    }

    /**
     * Returns the nearest charging station when multiple stations are present.
     */
    @Test
    public void testFindNearestChargingStationMultiple() {
        map.addEntity(new ChargingStation("Far",  new Vector2D(9, 9)));
        map.addEntity(new ChargingStation("Near", new Vector2D(1, 1)));

        Vector2D from = new Vector2D(0, 0);
        Vector2D nearest = map.findNearestChargingStation(from);
        assertEquals(new Vector2D(1, 1), nearest,
                "Should return the station with smaller Manhattan distance");
    }

    /**
     * When stations tie on Manhattan distance, the first added one should win.
     */
    @Test
    public void testFindNearestChargingStationTieKeepsFirstEncountered() {
        Vector2D first = new Vector2D(1, 0);
        Vector2D second = new Vector2D(0, 1);
        map.addEntity(new ChargingStation("First", first));
        map.addEntity(new ChargingStation("Second", second));

        assertEquals(first, map.findNearestChargingStation(new Vector2D(0, 0)));
    }

    /**
     * A DeliveryStation is NOT returned by findNearestChargingStation().
     */
    @Test
    public void testDeliveryStationNotReturnedAsCharger() {
        map.addEntity(new DeliveryStation("DS1", new Vector2D(2, 2)));
        assertNull(map.findNearestChargingStation(new Vector2D(0, 0)),
                "DeliveryStation must not be treated as a charging station");
    }
}
