package com.openrobotics.util;

import javafx.scene.image.Image;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Unit tests for {@link IconLoader} icon lookup and cache behavior.
 *
 * <p>This suite verifies cache hits for equivalent type keys, cache invalidation after clear, and
 * null/unknown-type handling contract.</p>
 */
public class IconLoaderTest {

    /**
     * Clears icon cache after each test to keep cache-state assertions isolated.
     */
    @AfterEach
    void clearCache() {
        IconLoader.clearCache();
    }

    /**
     * Verifies repeated lookups for the same supported type return the cached {@link Image}
     * instance, regardless of input case.
     */
    @Test
    void getIcon_returns_cached_instance_for_same_supported_type() {
        Image first = IconLoader.getIcon("ROBOT");
        Image second = IconLoader.getIcon("robot");

        assertNotNull(first);
        assertSame(first, second);
    }

    /**
     * Verifies clearing cache forces a new icon instance to be loaded on next request.
     */
    @Test
    void clearCache_forces_a_fresh_load() {
        Image first = IconLoader.getIcon("CHARGER");
        IconLoader.clearCache();
        Image second = IconLoader.getIcon("CHARGER");

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
    }

    /**
     * Verifies null and unknown icon types return {@code null} without throwing.
     */
    @Test
    void getIcon_returns_null_for_null_or_unknown_type() {
        assertNull(IconLoader.getIcon(null));
        assertNull(IconLoader.getIcon("NOT_A_REAL_TYPE"));
    }
}
