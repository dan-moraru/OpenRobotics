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

public class ViewportTipsTest {

    private List<String> originalTips;

    @BeforeEach
    void captureOriginalTips() {
        originalTips = new ArrayList<>(ViewportTips.getAllTips());
    }

    @AfterEach
    void restoreOriginalTips() {
        ViewportTips.setTips(originalTips);
    }

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

    @Test
    void addTip_ignores_blank_values_and_adds_valid_tip() {
        ViewportTips.setTips(List.of("base"));

        ViewportTips.addTip("   ");
        ViewportTips.addTip("added");

        assertEquals(List.of("base", "added"), ViewportTips.getAllTips());
    }

    @Test
    void setTips_filters_null_and_blank_entries_and_result_is_unmodifiable() {
        ViewportTips.setTips(Arrays.asList("keep", "", "  ", null, "also-keep"));

        assertEquals(List.of("keep", "also-keep"), ViewportTips.getAllTips());
        assertThrows(UnsupportedOperationException.class, () -> ViewportTips.getAllTips().add("new"));
    }
}
