package com.openrobotics.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Direction} enum.
 *
 */
public class DirectionTest {

    /**
     * UP should move zero columns right and one row up (dy = -1 in row-major grid).
     */
    @Test
    public void testUpDeltas() {
        assertEquals(0,  Direction.UP.dx, "UP.dx must be 0");
        assertEquals(-1, Direction.UP.dy, "UP.dy must be -1");
    }

    /**
     * DOWN should move zero columns right and one row down (dy = +1).
     */
    @Test
    public void testDownDeltas() {
        assertEquals(0,  Direction.DOWN.dx, "DOWN.dx must be 0");
        assertEquals(1,  Direction.DOWN.dy, "DOWN.dy must be 1");
    }

    /**
     * LEFT should move one column left and zero rows (dx = -1).
     */
    @Test
    public void testLeftDeltas() {
        assertEquals(-1, Direction.LEFT.dx, "LEFT.dx must be -1");
        assertEquals(0,  Direction.LEFT.dy, "LEFT.dy must be 0");
    }

    /**
     * RIGHT should move one column right and zero rows (dx = +1).
     */
    @Test
    public void testRightDeltas() {
        assertEquals(1, Direction.RIGHT.dx, "RIGHT.dx must be 1");
        assertEquals(0, Direction.RIGHT.dy, "RIGHT.dy must be 0");
    }

    /**
     * Exactly four directions must be defined.
     */
    @Test
    public void testFourDirections() {
        assertEquals(4, Direction.values().length, "There must be exactly 4 directions");
    }

    /**
     * The enum declaration order is relied on for deterministic neighbor traversal.
     */
    @Test
    public void testDirectionOrderIsStable() {
        assertArrayEquals(
                new Direction[]{ Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT },
                Direction.values()
        );
    }

    /**
     * All four cardinal directions must be present in values().
     */
    @Test
    public void testAllDirectionsPresent() {
        Direction[] dirs = Direction.values();
        boolean hasUp = false;
        boolean hasDown = false;
        boolean hasLeft = false;
        boolean hasRight = false;
        for (Direction d : dirs) {
            switch (d) {
                case UP    -> hasUp    = true;
                case DOWN  -> hasDown  = true;
                case LEFT  -> hasLeft  = true;
                case RIGHT -> hasRight = true;
            }
        }
        assertTrue(hasUp, "Direction.UP must be present");
        assertTrue(hasDown, "Direction.DOWN must be present");
        assertTrue(hasLeft, "Direction.LEFT must be present");
        assertTrue(hasRight, "Direction.RIGHT must be present");
    }

    /**
     * Opposite directions should sum to zero.
     */
    @Test
    public void testOppositeDirectionsSumToZero() {
        assertEquals(0, Direction.UP.dx + Direction.DOWN.dx);
        assertEquals(0, Direction.UP.dy + Direction.DOWN.dy);
        assertEquals(0, Direction.LEFT.dx + Direction.RIGHT.dx);
        assertEquals(0, Direction.LEFT.dy + Direction.RIGHT.dy);
    }

    /**
     * Every direction should move along exactly one axis by one grid step.
     */
    @Test
    public void testEachDirectionIsSingleAxisUnitStep() {
        for (Direction direction : Direction.values()) {
            assertEquals(1, Math.abs(direction.dx) + Math.abs(direction.dy),
                    "Each direction must move by exactly one tile");
        }
    }

    /**
     * Enum name lookup should resolve each constant as expected.
     */
    @Test
    public void testValueOfForEachDirection() {
        for (Direction direction : Direction.values()) {
            assertSame(direction, Direction.valueOf(direction.name()));
        }
    }
}
