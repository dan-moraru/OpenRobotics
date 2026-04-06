package com.openrobotics.util;

import javafx.scene.image.Image;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class IconLoaderTest {

    @AfterEach
    void clearCache() {
        IconLoader.clearCache();
    }

    @Test
    void getIcon_returns_cached_instance_for_same_supported_type() {
        Image first = IconLoader.getIcon("ROBOT");
        Image second = IconLoader.getIcon("robot");

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    void clearCache_forces_a_fresh_load() {
        Image first = IconLoader.getIcon("CHARGER");
        IconLoader.clearCache();
        Image second = IconLoader.getIcon("CHARGER");

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
    }

    @Test
    void getIcon_returns_null_for_null_or_unknown_type() {
        assertNull(IconLoader.getIcon(null));
        assertNull(IconLoader.getIcon("NOT_A_REAL_TYPE"));
    }
}
