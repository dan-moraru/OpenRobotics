package com.openrobotics.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ViewportTips} tip-deck management behavior.
 *
 * <p>This suite validates non-repeating draw behavior within a cycle, blank-input filtering for
 * mutation APIs, and immutability/normalization guarantees of the exposed tip list.</p>
 */
public class ViewportTipsTest {

    /** Snapshot of original shared tips list restored after each test. */
    private List<String> originalTips;

    /**
     * Captures current global tips before each test to avoid leaking state across tests.
     */
    @BeforeEach
    void captureOriginalTips() {
        originalTips = new ArrayList<>(ViewportTips.getAllTips());
    }

    /**
     * Restores original global tips after each test.
     */
    @AfterEach
    void restoreOriginalTips() {
        ViewportTips.setTips(originalTips);
    }

    /**
     * Verifies deck draws consume all available tips before repeats occur.
     */
    @Test
    void nextTip_uses_all_tips_before_any_repeat_when_deck_is_small() {
        ViewportTips.setTips(List.of("tip-a", "tip-b"));

        String first = ViewportTips.nextTip();
        String second = ViewportTips.nextTip();
        String third = ViewportTips.nextTip();

        Set<String> firstCycle = new HashSet<>(List.of(first, second));
        assertEquals(Set.of("tip-a", "tip-b"), firstCycle);
        assertTrue(Set.of("tip-a", "tip-b").contains(third));
    }

    /**
     * Verifies {@link ViewportTips#addTip(String)} ignores blank values and appends valid tips.
     */
    @Test
    void addTip_ignores_blank_values_and_adds_valid_tip() {
        ViewportTips.setTips(List.of("base"));

        ViewportTips.addTip("   ");
        ViewportTips.addTip("added");

        assertEquals(List.of("base", "added"), ViewportTips.getAllTips());
    }

    /**
     * Verifies {@link ViewportTips#setTips(List)} removes null/blank entries and publishes an
     * unmodifiable list view.
     */
    @Test
    void setTips_filters_null_and_blank_entries_and_result_is_unmodifiable() {
        ViewportTips.setTips(Arrays.asList("keep", "", "  ", null, "also-keep"));

        assertEquals(List.of("keep", "also-keep"), ViewportTips.getAllTips());
        assertThrows(UnsupportedOperationException.class, () -> ViewportTips.getAllTips().add("new"));
    }
}
