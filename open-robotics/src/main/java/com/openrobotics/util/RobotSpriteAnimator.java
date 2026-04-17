package com.openrobotics.util;

import com.openrobotics.common.Direction;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads robot and charger sprite sheets and slices the correct frame at runtime.
 *
 * <p>Sheet layout (robot sheets):
 * <pre>
 *   row 0 = South (DOWN)   row 1 = North (UP)
 *   row 2 = East  (RIGHT)  row 3 = West  (LEFT)
 *   columns = animation frames (0-based)
 * </pre>
 *
 * <p>Charger sheet: single row, columns = frames.
 */
public class RobotSpriteAnimator {

    private static final int FRAME_PX   = 256;   // each cell in the PNG is 256×256
    private static final int WALK_FRAMES  = 12;
    private static final int IDLE_FRAMES  = 12;
    private static final int CHARGE_FRAMES = 12;

    /** ms per frame for each animation type */
    private static final long WALK_FRAME_MS   = 80;   // 12 fps-ish walk
    private static final long IDLE_FRAME_MS   = 160;  // slow idle sway
    private static final long CHARGE_FRAME_MS = 400;  // slow charge fill

    // Sheet images — loaded lazily once
    private static Image walkSheet;
    private static Image walkBoxSheet;
    private static Image idleSheet;
    private static Image chargerSheet;
    private static boolean loaded = false;

    // Frame cache: "sheetKey_row_col" → WritableImage crop
    private static final Map<String, WritableImage> frameCache = new HashMap<>();

    private static final EnumMap<Direction, Integer> DIR_ROW = new EnumMap<>(Direction.class);
    static {
        DIR_ROW.put(Direction.DOWN,  0);  // South
        DIR_ROW.put(Direction.UP,    1);  // North
        DIR_ROW.put(Direction.RIGHT, 2);  // East
        DIR_ROW.put(Direction.LEFT,  3);  // West
    }

    public static void ensureLoaded() {
        if (loaded) return;
        walkSheet    = loadSheet("robot_walk.png");
        walkBoxSheet = loadSheet("robot_walk_box.png");
        idleSheet    = loadSheet("robot_idle.png");
        chargerSheet = loadSheet("charger_anim.png");
        loaded = true;
    }

    private static Image loadSheet(String filename) {
        try (var s = RobotSpriteAnimator.class.getResourceAsStream(
                "/com/openrobotics/img/" + filename)) {
            if (s == null) return null;
            return new Image(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Robot walk frame (or walk-with-box). */
    public static Image walkFrame(Direction dir, boolean carrying) {
        ensureLoaded();
        Image sheet = carrying ? walkBoxSheet : walkSheet;
        if (sheet == null) return null;
        int row = DIR_ROW.getOrDefault(dir, 0);
        int col = (int) ((System.currentTimeMillis() / WALK_FRAME_MS) % WALK_FRAMES);
        return crop(sheet, carrying ? "wb" : "w", row, col);
    }

    /** Robot idle frame. */
    public static Image idleFrame(Direction dir) {
        ensureLoaded();
        if (idleSheet == null) return null;
        int row = DIR_ROW.getOrDefault(dir, 0);
        int col = (int) ((System.currentTimeMillis() / IDLE_FRAME_MS) % IDLE_FRAMES);
        return crop(idleSheet, "i", row, col);
    }

    /** Charging-station animation frame (independent of direction). */
    public static Image chargerFrame() {
        ensureLoaded();
        if (chargerSheet == null) return null;
        int col = (int) ((System.currentTimeMillis() / CHARGE_FRAME_MS) % CHARGE_FRAMES);
        return crop(chargerSheet, "c", 0, col);
    }

    private static WritableImage crop(Image src, String key, int row, int col) {
        String cacheKey = key + "_" + row + "_" + col;
        return frameCache.computeIfAbsent(cacheKey, k -> {
            int x = col * FRAME_PX;
            int y = row * FRAME_PX;
            int maxW = (int) src.getWidth()  - x;
            int maxH = (int) src.getHeight() - y;
            if (maxW <= 0 || maxH <= 0) return null;
            int w = Math.min(FRAME_PX, maxW);
            int h = Math.min(FRAME_PX, maxH);
            return new WritableImage(src.getPixelReader(), x, y, w, h);
        });
    }
}
