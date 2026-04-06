package com.openrobotics.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ApplicationConfigTest {

    @Test
    void constructor_loads_nonBlank_values_from_classpath_resource() throws Exception {
        ApplicationConfig config = new ApplicationConfig();

        assertNotNull(config.getDbUrl());
        assertNotNull(config.getDbUser());
        assertNotNull(config.getDbPassword());
        assertFalse(config.getDbUrl().isBlank());
        assertFalse(config.getDbUser().isBlank());
        assertFalse(config.getDbPassword().isBlank());
    }

    @Test
    void loadFromClasspath_reads_expected_keys_and_ignores_comment_lines() throws Exception {
        ApplicationConfig config = new ApplicationConfig();
        Method method = ApplicationConfig.class.getDeclaredMethod("loadFromClasspath");
        method.setAccessible(true);

        Properties props = (Properties) method.invoke(config);

        assertNotNull(props.getProperty("db.url"));
        assertNotNull(props.getProperty("db.user"));
        assertNotNull(props.getProperty("db.password"));
        assertNull(props.getProperty("% db.url"));
        assertNull(props.getProperty("# db.url"));
    }

    @Test
    void envOrProp_returns_trimmed_property_value_when_env_missing() throws Exception {
        Method method = ApplicationConfig.class.getDeclaredMethod("envOrProp", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, "__OPENROBOTICS_TEST_ENV_KEY__", "  fallback-value  ");

        assertEquals("fallback-value", result);
    }
}
