package com.openrobotics.util;

import com.openrobotics.map.Map;
import com.openrobotics.map.MapEntity;
import com.openrobotics.map.Vector2D;
import com.openrobotics.map.entities.environment.Obstacle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

import java.util.EnumMap;

/**
 * Draws wall tiles that visually connect to their neighbours using pre-rendered
 * Blender sprites for every combination.
 *
 * <p>Bitmask encoding (4 bits → 16 sprites):
 * <pre>
 *   bit 0 (1) = N   (y-1 on grid = up   on screen)
 *   bit 1 (2) = S   (y+1 on grid = down on screen)
 *   bit 2 (4) = E   (x+1 on grid = right)
 *   bit 3 (8) = W   (x-1 on grid = left)
 * </pre>
 * Sprites are named {@code wall_0.png} … {@code wall_15.png} in the img resource folder.
 */
public class ConnectedWallRenderer {

    private static final int VARIANT_COUNT = 16;
    private static final Image[] VARIANTS   = new Image[VARIANT_COUNT];
    private static       boolean loaded     = false;

    /** Pre-load all 16 wall variant images (call once on init). */
    public static void ensureLoaded() {
        if (loaded) return;
        for (int i = 0; i < VARIANT_COUNT; i++) {
            String path = "/com/openrobotics/img/wall_" + i + ".png";
            try (var s = ConnectedWallRenderer.class.getResourceAsStream(path)) {
                VARIANTS[i] = (s != null) ? new Image(s) : null;
            } catch (Exception ignored) {}
        }
        loaded = true;
    }

    /**
     * Draw the correct wall sprite for {@code entity} at canvas position
     * ({@code sx}, {@code sy}) with size {@code tileSize × tileSize}.
     */
    public static void draw(GraphicsContext gc,
                            double sx, double sy, double tileSize,
                            MapEntity entity, Map map) {
        ensureLoaded();

        int ex = entity.getPosition().getX();
        int ey = entity.getPosition().getY();

        int mask = 0;
        if (isWall(map, ex,     ey - 1)) mask |= 1;   // N
        if (isWall(map, ex,     ey + 1)) mask |= 2;   // S
        if (isWall(map, ex + 1, ey    )) mask |= 4;   // E
        if (isWall(map, ex - 1, ey    )) mask |= 8;   // W

        Image img = VARIANTS[mask];
        if (img != null && !img.isError()) {
            gc.drawImage(img, sx, sy, tileSize, tileSize);
        } else {
            // Fallback: solid dark rect
            gc.setFill(javafx.scene.paint.Color.web("#6b5b4e"));
            gc.fillRect(sx, sy, tileSize, tileSize);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static boolean isWall(Map map, int x, int y) {
        if (map == null) return false;
        try {
            for (MapEntity e : map.getEntitiesAt(new Vector2D(x, y))) {
                if (isWallLike(e)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean isWallLike(MapEntity e) {
        if (e instanceof Obstacle) return true;
        String n = e.getName() == null ? "" : e.getName().toLowerCase();
        return n.contains("wall") || n.contains("obstacle");
    }
}
