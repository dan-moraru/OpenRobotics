package com.openrobotics.util;

import javafx.scene.image.Image;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads and caches entity icons from resources; all calls happen on the JavaFX thread. */
public class IconLoader {
    private static final Map<String, Optional<Image>> imageCache = new HashMap<>();

    private static final List<String> SUPPORTED_TYPES = List.of(
            "ROBOT", "CHARGER", "STATION", "DOCK", "WALL", "SHELF",
            "RACK", "OBSTACLE", "DELIVERY", "INTERSECTION"
    );

    /**
     * Returns the cached icon for the given type, loading it on first access.
     *
     * @param type the entity type string (e.g. {@code "ROBOT"}, {@code "RACK"}); case-insensitive
     * @return the icon {@link Image}, or {@code null} if the resource was not found
     */
    public static Image getIcon(String type) {
        if (type == null) return null;

        String key = type.toUpperCase();
        return imageCache
            .computeIfAbsent(key, k -> Optional.ofNullable(loadIconFromResource(k)))
            .orElse(null);
    }

    /** Pre-loads all standard icons to avoid first-use delays. */
    public static void preloadAllIcons() {
        for (String type : SUPPORTED_TYPES) {
            getIcon(type);
        }
    }

    /** Clears the icon cache; used in tests. */
    public static void clearCache() {
        imageCache.clear();
    }

    private static Image loadIconFromResource(String type) {
        try {
            String resourcePath = "/com/openrobotics/img/" + type.toLowerCase() + ".png";
            try (var stream = IconLoader.class.getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    return null;
                }
                return new Image(stream);
            }
        } catch (Exception e) {
            // Silently fail; null image will use fallback color
        }
        return null;
    }
}
