package com.openrobotics.util;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages and displays tips to guide the user in the viewport editor.
 * Tips are randomly selected from a configurable list.
 * This keeps UI hints in one easily editable location.
 */
public class ViewportTips {
    private static final CopyOnWriteArrayList<String> selectionTips = new CopyOnWriteArrayList<>(List.of(
            "Left-click an object to select it",
            "Selected objects show a green border",
            "Press Delete to remove selected object",
            "Drag objects to reposition them on the grid",
            "Double-click object tiles to open properties",
            "Cmd+C to copy, Cmd+V to paste selected",
            "Right-click and drag to pan the viewport",
            "Scroll to zoom in and out",
            "Objects snap to grid tiles automatically"
    ));

    /**
     * Gets a random selection tip.
     */
    public static String getRandomSelectionTip() {
        List<String> tips = selectionTips;
        if (tips.isEmpty()) return "Left-click to select";
        return tips.get(ThreadLocalRandom.current().nextInt(tips.size()));
    }

    /**
     * Adds a custom tip to the selection tips list.
     */
    public static void addTip(String tip) {
        if (tip != null && !tip.isBlank()) {
            selectionTips.add(tip);
        }
    }

    /**
     * Clears all tips and replaces with a custom set.
     */
    public static void setTips(List<String> tips) {
        selectionTips.clear();
        if (tips != null) {
            for (String tip : tips) {
                if (tip != null && !tip.isBlank()) {
                    selectionTips.add(tip);
                }
            }
        }
    }

    /**
     * Gets all currently registered tips.
     */
    public static List<String> getAllTips() {
        return Collections.unmodifiableList(selectionTips);
    }
}
