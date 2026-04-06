package com.openrobotics.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Vector2D}.
 *
 * Covers construction, getters, immutable arithmetic (add), Manhattan
 * distance, value-equality semantics (equals / hashCode), and toString.
 */
public class Vector2DTest {

    /**
     * Verifies that the constructor stores x and y correctly.
     */
    @Test
    public void testConstructorAndGetters() {
        Vector2D v = new Vector2D(3, 7);
        assertEquals(3, v.getX(), "getX() should return the x value passed to the constructor");
        assertEquals(7, v.getY(), "getY() should return the y value passed to the constructor");
    }

    /**
     * Verifies that the origin (0, 0) is constructed correctly.
     */
    @Test
    public void testOrigin() {
        Vector2D origin = new Vector2D(0, 0);
        assertEquals(0, origin.getX());
        assertEquals(0, origin.getY());
    }

    /**
     * Verifies construction with negative coordinates.
     */
    @Test
    public void testNegativeCoordinates() {
        Vector2D v = new Vector2D(-5, -10);
        assertEquals(-5, v.getX());
        assertEquals(-10, v.getY());
    }

    /**
     * Adding positive deltas should return a new vector with updated coordinates.
     */
    @Test
    public void testAddPositiveDeltas() {
        Vector2D v = new Vector2D(2, 3);
        Vector2D result = v.add(4, 5);
        assertEquals(6, result.getX());
        assertEquals(8, result.getY());
    }

    /**
     * Adding zero deltas returns a vector equal to the original.
     */
    @Test
    public void testAddZeroDeltas() {
        Vector2D v = new Vector2D(5, 5);
        Vector2D result = v.add(0, 0);
        assertEquals(v, result, "Adding zero should yield an equivalent vector");
    }

    /**
     * add(0, 0) should still return a new instance because Vector2D is immutable.
     */
    @Test
    public void testAddZeroReturnsNewInstance() {
        Vector2D v = new Vector2D(5, 5);
        Vector2D result = v.add(0, 0);
        assertNotSame(v, result);
    }

    /**
     * Adding negative deltas should correctly decrease coordinates.
     */
    @Test
    public void testAddNegativeDeltas() {
        Vector2D v = new Vector2D(10, 10);
        Vector2D result = v.add(-3, -7);
        assertEquals(7, result.getX());
        assertEquals(3, result.getY());
    }

    /**
     * add() must return a NEW object, not mutate the original.
     */
    @Test
    public void testAddImmutability() {
        Vector2D original = new Vector2D(1, 1);
        original.add(99, 99);
        assertEquals(1, original.getX(), "add() must not mutate the original vector");
        assertEquals(1, original.getY(), "add() must not mutate the original vector");
    }

    /**
     * add() composes coordinate-wise as expected across multiple calls.
     */
    @Test
    public void testAddComposition() {
        Vector2D v = new Vector2D(1, 2);
        assertEquals(new Vector2D(4, 6), v.add(1, 1).add(2, 3));
    }

    /**
     * Manhattan distance between identical vectors is zero.
     */
    @Test
    public void testManhattanDistanceToSelf() {
        Vector2D v = new Vector2D(4, 6);
        assertEquals(0, v.manhattanDistance(v));
    }

    /**
     * Basic case: distance between (0,0) and (3,4) is 7.
     */
    @Test
    public void testManhattanDistanceBasic() {
        Vector2D a = new Vector2D(0, 0);
        Vector2D b = new Vector2D(3, 4);
        assertEquals(7, a.manhattanDistance(b));
        assertEquals(7, b.manhattanDistance(a), "Distance should be symmetric");
    }

    /**
     * Distance should use absolute values, so negative-coordinate paths work.
     */
    @Test
    public void testManhattanDistanceWithNegativeCoords() {
        Vector2D a = new Vector2D(-2, -3);
        Vector2D b = new Vector2D(2, 3);
        assertEquals(10, a.manhattanDistance(b));
    }

    /**
     * Horizontal-only distance (same y).
     */
    @Test
    public void testManhattanDistanceHorizontal() {
        Vector2D a = new Vector2D(0, 5);
        Vector2D b = new Vector2D(8, 5);
        assertEquals(8, a.manhattanDistance(b));
    }

    /**
     * Vertical-only distance (same x).
     */
    @Test
    public void testManhattanDistanceVertical() {
        Vector2D a = new Vector2D(3, 0);
        Vector2D b = new Vector2D(3, 6);
        assertEquals(6, a.manhattanDistance(b));
    }

    /**
     * Two vectors with identical coordinates must be equal.
     */
    @Test
    public void testEqualsSameCoords() {
        Vector2D a = new Vector2D(2, 8);
        Vector2D b = new Vector2D(2, 8);
        assertEquals(a, b);
    }

    /**
     * Two vectors with different x are not equal.
     */
    @Test
    public void testNotEqualDifferentX() {
        assertNotEquals(new Vector2D(1, 5), new Vector2D(2, 5));
    }

    /**
     * Two vectors with different y are not equal.
     */
    @Test
    public void testNotEqualDifferentY() {
        assertNotEquals(new Vector2D(5, 1), new Vector2D(5, 2));
    }

    /**
     * A vector is equal to itself (reflexive).
     */
    @Test
    public void testEqualsReflexive() {
        Vector2D v = new Vector2D(7, 3);
        assertEquals(v, v);
    }

    /**
     * Equality should be symmetric.
     */
    @Test
    public void testEqualsSymmetric() {
        Vector2D a = new Vector2D(7, 3);
        Vector2D b = new Vector2D(7, 3);
        assertEquals(a, b);
        assertEquals(b, a);
    }

    /**
     * Equality should be transitive.
     */
    @Test
    public void testEqualsTransitive() {
        Vector2D a = new Vector2D(7, 3);
        Vector2D b = new Vector2D(7, 3);
        Vector2D c = new Vector2D(7, 3);
        assertEquals(a, b);
        assertEquals(b, c);
        assertEquals(a, c);
    }

    /**
     * A vector is never equal to null.
     */
    @Test
    public void testEqualsNull() {
        assertNotEquals(null, new Vector2D(0, 0));
    }

    /**
     * A vector is never equal to an object of a different type.
     */
    @Test
    public void testEqualsDifferentType() {
        assertNotEquals("(1, 1)", new Vector2D(1, 1));
    }

    /**
     * Equal vectors must have the same hashCode (hash contract).
     */
    @Test
    public void testHashCodeConsistencyWithEquals() {
        Vector2D a = new Vector2D(3, 9);
        Vector2D b = new Vector2D(3, 9);
        assertEquals(a.hashCode(), b.hashCode(),
                "Equal vectors must have the same hashCode");
    }

    /**
     * hashCode() should be stable across repeated calls.
     */
    @Test
    public void testHashCodeStableAcrossCalls() {
        Vector2D v = new Vector2D(8, 2);
        int hash = v.hashCode();
        assertEquals(hash, v.hashCode());
        assertEquals(hash, v.hashCode());
    }

    /**
     * Distinct coordinate pairs (typically) produce different hashes.
     */
    @Test
    public void testHashCodeDifferentForDistinctVectors() {
        assertNotEquals(
                new Vector2D(1, 2).hashCode(),
                new Vector2D(2, 1).hashCode(),
                "Hashes of (1,2) and (2,1) should differ");
    }

    /**
     * toString() should produce the format "(x, y)".
     */
    @Test
    public void testToString() {
        Vector2D v = new Vector2D(4, 9);
        assertEquals("(4, 9)", v.toString());
    }

    /**
     * toString() at the origin.
     */
    @Test
    public void testToStringOrigin() {
        assertEquals("(0, 0)", new Vector2D(0, 0).toString());
    }
}
