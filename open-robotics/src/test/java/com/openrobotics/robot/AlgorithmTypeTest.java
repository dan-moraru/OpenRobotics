package com.openrobotics.robot;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AlgorithmType}.
 *
 * <p>Covers all canonical enum values, all known config-string aliases
 * (case-insensitive), the null/unknown-string fallback to {@code NONE},
 * and leading/trailing whitespace tolerance.
 */
public class AlgorithmTypeTest {

    // ── Canonical names ───────────────────────────────────────────────────────

    /**
     * "GREEDY" maps to {@link AlgorithmType#GREEDY}.
     */
    @Test
    public void testGreedyCanonical() {
        assertEquals(AlgorithmType.GREEDY, AlgorithmType.fromConfigString("GREEDY"));
    }

    /**
     * "BUG" maps to {@link AlgorithmType#BUG}.
     */
    @Test
    public void testBugCanonical() {
        assertEquals(AlgorithmType.BUG, AlgorithmType.fromConfigString("BUG"));
    }

    /**
     * "RTA_STAR" maps to {@link AlgorithmType#RTA_STAR}.
     */
    @Test
    public void testRtaStarCanonical() {
        assertEquals(AlgorithmType.RTA_STAR, AlgorithmType.fromConfigString("RTA_STAR"));
    }

    /**
     * "RANDOM" maps to {@link AlgorithmType#RANDOM}.
     */
    @Test
    public void testRandomCanonical() {
        assertEquals(AlgorithmType.RANDOM, AlgorithmType.fromConfigString("RANDOM"));
    }

    // ── Class-name aliases ────────────────────────────────────────────────────

    /**
     * "GREEDYNAVIGATIONSTRATEGY" maps to {@link AlgorithmType#GREEDY}.
     */
    @Test
    public void testGreedyClassNameAlias() {
        assertEquals(AlgorithmType.GREEDY, AlgorithmType.fromConfigString("GREEDYNAVIGATIONSTRATEGY"));
    }

    /**
     * "BUGNAVIGATIONSTRATEGY" maps to {@link AlgorithmType#BUG}.
     */
    @Test
    public void testBugClassNameAlias() {
        assertEquals(AlgorithmType.BUG, AlgorithmType.fromConfigString("BUGNAVIGATIONSTRATEGY"));
    }

    /**
     * "RTASTARNAVIGATIONSTRATEGY" maps to {@link AlgorithmType#RTA_STAR}.
     */
    @Test
    public void testRtaStarClassNameAlias() {
        assertEquals(AlgorithmType.RTA_STAR, AlgorithmType.fromConfigString("RTASTARNAVIGATIONSTRATEGY"));
    }

    /**
     * "RANDOMNAVIGATIONSTRATEGY" maps to {@link AlgorithmType#RANDOM}.
     */
    @Test
    public void testRandomClassNameAlias() {
        assertEquals(AlgorithmType.RANDOM, AlgorithmType.fromConfigString("RANDOMNAVIGATIONSTRATEGY"));
    }

    /**
     * Lower-case input must still resolve correctly.
     */
    @Test
    public void testLowerCaseInput() {
        assertEquals(AlgorithmType.GREEDY, AlgorithmType.fromConfigString("greedy"));
        assertEquals(AlgorithmType.RANDOM, AlgorithmType.fromConfigString("random"));
    }

    /**
     * Mixed-case input must still resolve correctly.
     */
    @Test
    public void testMixedCaseInput() {
        assertEquals(AlgorithmType.GREEDY, AlgorithmType.fromConfigString("Greedy"));
        assertEquals(AlgorithmType.BUG,    AlgorithmType.fromConfigString("Bug"));
    }

    /**
     * Leading and trailing whitespace should be trimmed before lookup.
     */
    @Test
    public void testTrimmedWhitespace() {
        assertEquals(AlgorithmType.GREEDY, AlgorithmType.fromConfigString("  GREEDY  "));
        assertEquals(AlgorithmType.RANDOM, AlgorithmType.fromConfigString(" random "));
    }

    /**
     * Tabs and newlines should also be trimmed before lookup.
     */
    @Test
    public void testNonSpaceWhitespaceIsTrimmed() {
        assertEquals(AlgorithmType.BUG, AlgorithmType.fromConfigString("\nBUG\t"));
        assertEquals(AlgorithmType.RTA_STAR, AlgorithmType.fromConfigString("\t rta_star \n"));
    }

    /**
     * Aliases should resolve correctly even when case and surrounding whitespace vary.
     */
    @Test
    public void testAliasesRemainCaseInsensitiveAfterTrimming() {
        Map<String, AlgorithmType> aliases = Map.of(
                "  greedynavigationstrategy\t", AlgorithmType.GREEDY,
                "\nBugNavigationStrategy ", AlgorithmType.BUG,
                " rtastarnavigationstrategy ", AlgorithmType.RTA_STAR,
                "\tRaNdOmNaViGaTiOnStRaTeGy\n", AlgorithmType.RANDOM
        );

        aliases.forEach((input, expected) ->
                assertEquals(expected, AlgorithmType.fromConfigString(input),
                        () -> "Expected alias to map correctly: [" + input + "]"));
    }

    /**
     * A null input should return {@link AlgorithmType#NONE}.
     */
    @Test
    public void testNullReturnsNone() {
        assertEquals(AlgorithmType.NONE, AlgorithmType.fromConfigString(null));
    }

    /**
     * An unrecognised string should return {@link AlgorithmType#NONE}.
     */
    @Test
    public void testUnknownStringReturnsNone() {
        assertEquals(AlgorithmType.NONE, AlgorithmType.fromConfigString("UNKNOWN_ALGO"));
    }

    /**
     * An empty string should return {@link AlgorithmType#NONE}.
     */
    @Test
    public void testEmptyStringReturnsNone() {
        assertEquals(AlgorithmType.NONE, AlgorithmType.fromConfigString(""));
    }

    /**
     * A string that becomes empty after trimming should return {@link AlgorithmType#NONE}.
     */
    @Test
    public void testBlankWhitespaceReturnsNone() {
        assertEquals(AlgorithmType.NONE, AlgorithmType.fromConfigString("   \t  \n "));
    }

    /**
     * Similar-but-invalid names should not be accepted.
     */
    @Test
    public void testNearMatchesStillReturnNone() {
        assertEquals(AlgorithmType.NONE, AlgorithmType.fromConfigString("GREEDY_NAVIGATION_STRATEGY"));
        assertEquals(AlgorithmType.NONE, AlgorithmType.fromConfigString("RTA STAR"));
        assertEquals(AlgorithmType.NONE, AlgorithmType.fromConfigString("BUGSTRATEGY"));
    }

    /**
     * The enum must declare exactly the five expected values.
     */
    @Test
    public void testEnumValueCount() {
        assertEquals(5, AlgorithmType.values().length);
    }

    /**
     * Every enum constant name should resolve back through the config parser.
     *
     * <p>This helps catch future enum additions that are not wired into
     * {@link AlgorithmType#fromConfigString(String)}.
     */
    @Test
    public void testEveryEnumNameIsHandledByFromConfigString() {
        for (AlgorithmType type : AlgorithmType.values()) {
            assertEquals(type, AlgorithmType.fromConfigString(type.name()),
                    () -> "Expected enum name to resolve for " + type);
        }
    }
}
