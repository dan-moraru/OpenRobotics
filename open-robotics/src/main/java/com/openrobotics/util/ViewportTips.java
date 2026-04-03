package com.openrobotics.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages viewport editor tips shown to the user.
 * Tips cycle sequentially through a shuffled deck so no tip
 * repeats until all others have been shown.
 */
public class ViewportTips {

    private static final CopyOnWriteArrayList<String> selectionTips = new CopyOnWriteArrayList<>(List.of(
            "Left-click an object to select it",
            "Selected objects show a highlight border",
            "Press Delete to remove the selected object",
            "Drag objects to reposition them on the grid",
            "Double-click object tiles to open properties",
            "Cmd+C to copy, Cmd+V to paste selected",
            "Right-click and drag to pan the viewport",
            "Scroll to zoom in and out",
            "Objects snap to grid tiles automatically"
    ));

    /* Shuffled deck for sequential non-repeating rotation */
    private static final List<String> deck = new ArrayList<>();
    private static int deckIndex = 0;

    /**
     * Returns the next tip in shuffled sequence.
     * Reshuffles the deck once all tips have been shown.
     */
    public static String nextTip() {
        if (deck.isEmpty() || deckIndex >= deck.size()) {
            deck.clear();
            deck.addAll(selectionTips);
            Collections.shuffle(deck, new java.util.Random());
            deckIndex = 0;
        }
        return deck.get(deckIndex++);
    }

    /**
     * Returns a single random tip without advancing the deck.
     * Use for one-off display (e.g. on first load).
     */
    public static String getRandomSelectionTip() {
        if (selectionTips.isEmpty()) return "Left-click to select";
        return selectionTips.get(ThreadLocalRandom.current().nextInt(selectionTips.size()));
    }

    /** Adds a custom tip to the pool. */
    public static void addTip(String tip) {
        if (tip != null && !tip.isBlank()) {
            selectionTips.add(tip);
            deck.clear(); // force deck rebuild on next call
        }
    }

    /** Replaces all tips with a custom set. */
    public static void setTips(List<String> tips) {
        selectionTips.clear();
        deck.clear();
        if (tips != null) {
            for (String tip : tips) {
                if (tip != null && !tip.isBlank()) selectionTips.add(tip);
            }
        }
    }

    /** Returns an unmodifiable view of all registered tips. */
    public static List<String> getAllTips() {
        return Collections.unmodifiableList(selectionTips);
    }
}
