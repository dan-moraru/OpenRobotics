package com.openrobotics.simulationcore;

import com.openrobotics.map.Map;
import com.openrobotics.map.Tile;
import com.openrobotics.map.Vector2D;
import com.openrobotics.robot.Robot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link CoordinationPolicy} interface.
 *
 * <p>Focuses on the {@code noOp()} static factory method, which must pass
 * all non-null intentions through unchanged.
 */
public class CoordinationPolicyTest {

    private static Map testMap() {
        return new Map(10, 10);
    }

    /**
     * noOp() must return a non-null policy instance.
     */
    @Test
    public void testNoOpIsNotNull() {
        assertNotNull(CoordinationPolicy.noOp());
    }

    /**
     * noOp() should return the shared singleton policy instance.
     */
    @Test
    public void testNoOpReturnsSameSingletonInstance() {
        assertSame(CoordinationPolicy.noOp(), CoordinationPolicy.noOp());
    }

    /**
     * noOp() applied to an empty array returns an empty array.
     */
    @Test
    public void testNoOpEmptyInput() {
        assertEquals(0, CoordinationPolicy.noOp().apply(testMap(), new MoveIntention[0]).length);
    }

    /**
     * noOp() applied to a null input returns an empty array without throwing.
     */
    @Test
    public void testNoOpNullInput() {
        assertDoesNotThrow(() -> {
            MoveIntention[] result = CoordinationPolicy.noOp().apply(testMap(), null);
            assertEquals(0, result.length);
        });
    }

    /**
     * noOp() passes through a single intention unchanged.
     */
    @Test
    public void testNoOpSingleIntentionPassesThrough() {
        Robot robot = new Robot("R1", new Vector2D(0, 0));
        Tile  from  = new Tile(0, 0);
        Tile  to    = new Tile(1, 0);
        MoveIntention original = new MoveIntention(from, to, robot);

        MoveIntention[] result = CoordinationPolicy.noOp().apply(testMap(), new MoveIntention[]{ original });

        assertEquals(1, result.length);
        assertSame(original, result[0],
                "noOp() must return the original intention object unchanged");
    }

    /**
     * noOp() should return a new array object rather than the original input array.
     */
    @Test
    public void testNoOpReturnsCopiedArray() {
        Robot robot = new Robot("R1", new Vector2D(0, 0));
        MoveIntention original = new MoveIntention(new Tile(0, 0), new Tile(1, 0), robot);
        MoveIntention[] input = new MoveIntention[]{ original };

        MoveIntention[] result = CoordinationPolicy.noOp().apply(testMap(), input);

        assertNotSame(input, result);
        assertSame(original, result[0]);
    }

    /**
     * noOp() passes through multiple intentions in the same order.
     */
    @Test
    public void testNoOpMultipleIntentionsOrder() {
        Robot r1 = new Robot("R1", new Vector2D(0, 0));
        Robot r2 = new Robot("R2", new Vector2D(5, 5));

        MoveIntention mi1 = new MoveIntention(new Tile(0, 0), new Tile(1, 0), r1);
        MoveIntention mi2 = new MoveIntention(new Tile(5, 5), new Tile(6, 5), r2);

        MoveIntention[] result = CoordinationPolicy.noOp().apply(testMap(), new MoveIntention[]{ mi1, mi2 });

        assertEquals(2, result.length);
        assertSame(mi1, result[0]);
        assertSame(mi2, result[1]);
    }

    /**
     * noOp() strips out null entries from the input array.
     */
    @Test
    public void testNoOpStripsNullEntries() {
        Robot robot = new Robot("R1", new Vector2D(0, 0));
        MoveIntention valid = new MoveIntention(new Tile(0, 0), new Tile(1, 0), robot);

        MoveIntention[] result = CoordinationPolicy.noOp()
                .apply(testMap(), new MoveIntention[]{ null, valid, null });

        assertEquals(1, result.length,
                "noOp() must filter out null entries");
        assertSame(valid, result[0]);
    }

    /**
     * noOp() does not depend on the map argument.
     */
    @Test
    public void testNoOpWorksWithNullMap() {
        Robot robot = new Robot("R1", new Vector2D(0, 0));
        MoveIntention valid = new MoveIntention(new Tile(0, 0), new Tile(1, 0), robot);

        MoveIntention[] result = CoordinationPolicy.noOp().apply(null, new MoveIntention[]{ valid });

        assertEquals(1, result.length);
        assertSame(valid, result[0]);
    }
}
