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
            // Navigation
            "Right-click + drag to pan the viewport freely",
            "Scroll wheel zooms in/out toward the crosshair",
            "⌖ resets zoom and re-centres the map",
            "⊕ / ⊖ zoom toward the crosshair, not the mouse",
            // Editing
            "Left-click an object to select it and view its properties",
            "Drag a selected object to a new tile — it snaps automatically",
            "Press Delete or Backspace to remove the selected object",
            "Cmd/Ctrl+C copies, Cmd/Ctrl+V pastes one tile away",
                "The Outliner filters both map objects and queued tasks",
                "Task entries in the Outliner are read-only; edit the map object instead",
                "Double-click an Add Object tile to open its description before placing it",
                "The Properties panel shows exact tile coordinates and editable robot settings",
            // Drag and drop
            "Drag any tile from the sidebar — the green highlight shows where it will land",
            "Objects can only share a tile if one is a Robot and the other a Station",
            "Chargers and delivery stations can coexist with robots on the same tile",
                "If a drop is blocked, the console explains which occupancy rule was violated",
                "When manual dropoff assignment is on, every rack needs at least one live delivery station",
            // Simulation
            "Press ▶ to start — robots will immediately begin picking up tasks",
            "⏸ pauses without losing your tick count; ▶ resumes from the same point",
            "▶▶ steps exactly one tick — useful for tracing robot decisions frame by frame",
            "↺ fully reloads the config from disk and resets the simulation",
            "x1/x2/x3 multiply simulation speed — useful for long workloads",
                "If the editor is locked, stop or reset the simulation before moving objects",
            // Strategy tips
            "Higher-priority tasks are assigned to robots first",
            "Robots re-route automatically when stuck for several ticks",
            "Reservation-K coordination reduces head-on collisions between robots",
            "Placing chargers near busy corridors reduces robot idle time",
            "More robots increase throughput but also raise collision risk",
                "Use manual dropoff assignment only when you want fine-grained rack control",
            // Outliner & properties
            "Use the Outliner to find any object by name — type to filter",
            "Select a task in the Outliner to see its pickup, dropoff, and priority",
            "The Properties panel lets you reposition objects precisely by tile number",
            // UI
            "View → Sidebar collapses the panel — the viewport expands to fill the space",
            "View → Console collapses the log — drag the divider to resize it manually",
                "The console logs every placement, move, blocked-tile warning, and rejected drag",
                "A fresh selection tip appears below the viewport when nothing is selected"
    ));

    /* Shuffled deck for sequential non-repeating rotation */
    private static final List<String> deck = new ArrayList<>();
    private static int deckIndex = 0;
    private static final Object lock = new Object();

    /**
     * Returns the next tip in shuffled sequence.
     * Reshuffles the deck once all tips have been shown.
     */
    public static String nextTip() {
        synchronized (lock) {
            if (selectionTips.isEmpty()) {
                return "Left-click to select";
            }
            if (deck.isEmpty() || deckIndex >= deck.size()) {
                deck.clear();
                deck.addAll(selectionTips);
                Collections.shuffle(deck, ThreadLocalRandom.current());
                deckIndex = 0;
            }
            return deck.get(deckIndex++);
        }
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
            synchronized (lock) {
                selectionTips.add(tip);
                deck.clear(); // force deck rebuild on next call
                deckIndex = 0;
            }
        }
    }

    /** Replaces all tips with a custom set. */
    public static void setTips(List<String> tips) {
        List<String> filtered = new ArrayList<>();
        if (tips != null) {
            for (String tip : tips) {
                if (tip != null && !tip.isBlank()) filtered.add(tip);
            }
        }
        synchronized (lock) {
            selectionTips.clear();
            selectionTips.addAll(filtered);
            deck.clear();
            deck.addAll(selectionTips);
            Collections.shuffle(deck, ThreadLocalRandom.current());
            deckIndex = 0;
        }
    }

    /** Returns an unmodifiable view of all registered tips. */
    public static List<String> getAllTips() {
        return Collections.unmodifiableList(selectionTips);
    }
}
