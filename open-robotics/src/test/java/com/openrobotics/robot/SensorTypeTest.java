package com.openrobotics.robot;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SensorType}.
 *
 * Covers all canonical enum values, all known config-string aliases
 * (case-insensitive), the null/unknown-string fallback to {@code NONE},
 * and leading/trailing whitespace tolerance.
 */
public class SensorTypeTest {

    /**
     * "PROXIMITY" maps to {@link SensorType#PROXIMITY}.
     */
    @Test
    public void testProximityCanonical() {
        assertEquals(SensorType.PROXIMITY, SensorType.fromConfigString("PROXIMITY"));
    }

    /**
     * "RANGE" maps to {@link SensorType#RANGE}.
     */
    @Test
    public void testRangeCanonical() {
        assertEquals(SensorType.RANGE, SensorType.fromConfigString("RANGE"));
    }

    /**
     * "PROXIMITYSENSOR" maps to {@link SensorType#PROXIMITY}.
     */
    @Test
    public void testProximityClassNameAlias() {
        assertEquals(SensorType.PROXIMITY, SensorType.fromConfigString("PROXIMITYSENSOR"));
    }

    /**
     * "RANGESENSOR" maps to {@link SensorType#RANGE}.
     */
    @Test
    public void testRangeClassNameAlias() {
        assertEquals(SensorType.RANGE, SensorType.fromConfigString("RANGESENSOR"));
    }

    /**
     * Lower-case input should resolve correctly.
     */
    @Test
    public void testLowerCaseInput() {
        assertEquals(SensorType.PROXIMITY, SensorType.fromConfigString("proximity"));
        assertEquals(SensorType.RANGE,     SensorType.fromConfigString("range"));
    }

    /**
     * Mixed-case input should resolve correctly.
     */
    @Test
    public void testMixedCaseInput() {
        assertEquals(SensorType.PROXIMITY, SensorType.fromConfigString("Proximity"));
        assertEquals(SensorType.RANGE,     SensorType.fromConfigString("Range"));
    }

    /**
     * Leading and trailing whitespace should be trimmed before lookup.
     */
    @Test
    public void testWhitespaceTrimmed() {
        assertEquals(SensorType.PROXIMITY, SensorType.fromConfigString("  PROXIMITY  "));
        assertEquals(SensorType.RANGE,     SensorType.fromConfigString(" range "));
    }

    /**
     * Tabs and newlines should also be trimmed before lookup.
     */
    @Test
    public void testNonSpaceWhitespaceTrimmed() {
        assertEquals(SensorType.PROXIMITY, SensorType.fromConfigString("\nPROXIMITY\t"));
        assertEquals(SensorType.RANGE, SensorType.fromConfigString("\t range \n"));
    }

    /**
     * Aliases should resolve even when case and surrounding whitespace vary.
     */
    @Test
    public void testAliasesRemainCaseInsensitiveAfterTrimming() {
        Map<String, SensorType> aliases = Map.of(
                "  proximitysensor\t", SensorType.PROXIMITY,
                "\nRangeSensor ", SensorType.RANGE
        );

        aliases.forEach((input, expected) ->
                assertEquals(expected, SensorType.fromConfigString(input),
                        () -> "Expected alias to map correctly: [" + input + "]"));
    }

    /**
     * A null input should return {@link SensorType#NONE}.
     */
    @Test
    public void testNullReturnsNone() {
        assertEquals(SensorType.NONE, SensorType.fromConfigString(null));
    }

    /**
     * An unrecognised string should return {@link SensorType#NONE}.
     */
    @Test
    public void testUnknownStringReturnsNone() {
        assertEquals(SensorType.NONE, SensorType.fromConfigString("LIDAR"));
    }

    /**
     * An empty string should return {@link SensorType#NONE}.
     */
    @Test
    public void testEmptyStringReturnsNone() {
        assertEquals(SensorType.NONE, SensorType.fromConfigString(""));
    }

    /**
     * A string that becomes empty after trimming should return {@link SensorType#NONE}.
     */
    @Test
    public void testBlankWhitespaceReturnsNone() {
        assertEquals(SensorType.NONE, SensorType.fromConfigString("   \t  \n "));
    }

    /**
     * Similar-but-invalid names should not be accepted.
     */
    @Test
    public void testNearMatchesStillReturnNone() {
        assertEquals(SensorType.NONE, SensorType.fromConfigString("PROXIMITY_SENSOR"));
        assertEquals(SensorType.NONE, SensorType.fromConfigString("RANGE SENSOR"));
        assertEquals(SensorType.NONE, SensorType.fromConfigString("PROX"));
    }

    /**
     * The enum must declare exactly the three expected values.
     */
    @Test
    public void testEnumValueCount() {
        assertEquals(3, SensorType.values().length);
    }

    /**
     * Every enum constant name should resolve back through the config parser.
     */
    @Test
    public void testEveryEnumNameIsHandledByFromConfigString() {
        for (SensorType type : SensorType.values()) {
            assertEquals(type, SensorType.fromConfigString(type.name()),
                    () -> "Expected enum name to resolve for " + type);
        }
    }
}
