package com.openrobotics.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Tile}.
 */
public class TileTest {

    /**
     * A new tile stores its coordinates and starts unoccupied.
     */
    @Test
    public void testConstructorSetsCoordinates() {
        Tile tile = new Tile(5, 3);
        assertEquals(5, tile.getX());
        assertEquals(3, tile.getY());
    }

    /**
     * A newly created tile must not be occupied by default.
     */
    @Test
    public void testNewTileIsNotOccupied() {
        Tile tile = new Tile(0, 0);
        assertFalse(tile.isOccupied(), "New tiles must start unoccupied");
    }

    /**
     * Tile at origin (0, 0) should store 0 for both coordinates.
     */
    @Test
    public void testOriginTile() {
        Tile tile = new Tile(0, 0);
        assertEquals(0, tile.getX());
        assertEquals(0, tile.getY());
    }

    /**
     * Negative coordinates are currently allowed and should be stored verbatim.
     */
    @Test
    public void testNegativeCoordinatesAreStored() {
        Tile tile = new Tile(-2, -5);
        assertEquals(-2, tile.getX());
        assertEquals(-5, tile.getY());
        assertEquals(new Vector2D(-2, -5), tile.getPosition());
    }

    /**
     * Setting occupied to {@code true} should be reflected by {@code isOccupied()}.
     */
    @Test
    public void testSetOccupiedTrue() {
        Tile tile = new Tile(2, 4);
        tile.setOccupied(true);
        assertTrue(tile.isOccupied());
    }

    /**
     * Setting occupied back to {@code false} should clear the flag.
     */
    @Test
    public void testSetOccupiedFalse() {
        Tile tile = new Tile(1, 1);
        tile.setOccupied(true);
        tile.setOccupied(false);
        assertFalse(tile.isOccupied(), "setOccupied(false) must clear the occupied flag");
    }

    /**
     * Multiple transitions between occupied states work correctly.
     */
    @Test
    public void testOccupiedToggle() {
        Tile tile = new Tile(3, 3);
        tile.setOccupied(true);
        assertTrue(tile.isOccupied());
        tile.setOccupied(false);
        assertFalse(tile.isOccupied());
        tile.setOccupied(true);
        assertTrue(tile.isOccupied());
    }

    /**
     * Changing occupancy must not alter the tile's coordinates.
     */
    @Test
    public void testSetOccupiedDoesNotChangeCoordinates() {
        Tile tile = new Tile(8, 9);

        tile.setOccupied(true);
        tile.setOccupied(false);

        assertEquals(8, tile.getX());
        assertEquals(9, tile.getY());
    }

    /**
     * getPosition() must return a Vector2D whose coordinates match the tile's.
     */
    @Test
    public void testGetPositionMatchesTileCoords() {
        Tile tile = new Tile(6, 9);
        Vector2D pos = tile.getPosition();
        assertNotNull(pos);
        assertEquals(6, pos.getX());
        assertEquals(9, pos.getY());
    }

    /**
     * getPosition() should return the correct Vector2D for the origin tile.
     */
    @Test
    public void testGetPositionAtOrigin() {
        Tile tile = new Tile(0, 0);
        assertEquals(new Vector2D(0, 0), tile.getPosition());
    }

    /**
     * Each call to getPosition() should return an object equal (by value) to
     * the tile coordinates, even if a new instance is returned each time.
     */
    @Test
    public void testGetPositionValueEquality() {
        Tile tile = new Tile(4, 7);
        Vector2D p1 = tile.getPosition();
        Vector2D p2 = tile.getPosition();
        assertEquals(p1, p2, "Repeated getPosition() calls must yield equal Vector2D objects");
    }

    /**
     * getPosition() currently returns a fresh Vector2D instance on each call.
     */
    @Test
    public void testGetPositionReturnsNewInstanceEachTime() {
        Tile tile = new Tile(4, 7);
        Vector2D p1 = tile.getPosition();
        Vector2D p2 = tile.getPosition();

        assertNotSame(p1, p2);
    }

    /**
     * The Vector2D returned by getPosition() reflects the tile coordinates even after occupancy changes.
     */
    @Test
    public void testGetPositionUnaffectedByOccupancy() {
        Tile tile = new Tile(10, 11);
        tile.setOccupied(true);

        assertEquals(new Vector2D(10, 11), tile.getPosition());
    }
}
