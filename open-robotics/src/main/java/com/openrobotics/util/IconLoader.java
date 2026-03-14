package com.openrobotics.util;

import javafx.scene.image.Image;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for loading and caching object icons.
 * Provides a unified interface for retrieving icons for different entity types.
 * Icons are cached after first load to avoid repeated file I/O.
 */
public class IconLoader {
    private static final Map<String, Image> imageCache = new ConcurrentHashMap<>();

    // Standard object types with icon assets
    private static final List<String> SUPPORTED_TYPES = List.of(
            "ROBOT", "CHARGER", "STATION", "DOCK", "WALL", "SHELF",
            "RACK", "OBSTACLE", "DELIVERY"
    );

    /**
     * Gets or loads an icon for the specified object type.
     * Icons are cached; subsequent calls return from cache.
     *
     * @param type the object type (e.g., "ROBOT", "CHARGER")
     * @return the icon image, or null if not found
     */
    public static Image getIcon(String type) {
        if (type == null) return null;

        String key = type.toUpperCase();
        return imageCache.computeIfAbsent(key, IconLoader::loadIconFromResource);
    }

    /**
     * Pre-loads all standard icons at startup.
     * Call this during application initialization to avoid load delays.
     */
    public static void preloadAllIcons() {
        for (String type : SUPPORTED_TYPES) {
            getIcon(type);
        }
    }

    /**
     * Clears the icon cache.
     * Useful for testing or if icons change at runtime.
     */
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
