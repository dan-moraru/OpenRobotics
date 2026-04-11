package com.openrobotics.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApplicationConfigTest {

    /** True when application.config on the classpath defines all three db.* keys. */
    private static boolean hasClasspathDbKeys(Properties props) {
        return props.getProperty("db.url") != null
                && props.getProperty("db.user") != null
                && props.getProperty("db.password") != null;
    }

    /** True when DB_URL, DB_USER, and DB_PASSWORD are all set and non-blank (e.g. CI secrets). */
    private static boolean dbEnvFullySet() {
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String pw = System.getenv("DB_PASSWORD");
        return url != null && !url.isBlank()
                && user != null && !user.isBlank()
                && pw != null && !pw.isBlank();
    }

    /* Loads properties from application.config on the classpath. */
    private static Properties loadFromClasspath(ApplicationConfig config) throws Exception {
        Method method = ApplicationConfig.class.getDeclaredMethod("loadFromClasspath");
        method.setAccessible(true);
        return (Properties) method.invoke(config);
    }

    /**
     * Tests that the ApplicationConfig constructor loads non-blank values from the application.config on the classpath.
     * @throws Exception if there is an error loading the properties from the application.config on the classpath
     */
    @Test
    void constructor_loads_nonBlank_values_from_classpath_resource() throws Exception {
        ApplicationConfig config = new ApplicationConfig();
        Properties props = loadFromClasspath(config);

        assertTrue(
                hasClasspathDbKeys(props) || dbEnvFullySet(),
                "Either application.config on the classpath must define db.url, db.user, and db.password, "
                        + "or DB_URL, DB_USER, and DB_PASSWORD must all be set");

        assertNotNull(config.getDbUrl());
        assertNotNull(config.getDbUser());
        assertNotNull(config.getDbPassword());
        assertFalse(config.getDbUrl().isBlank());
        assertFalse(config.getDbUser().isBlank());
        assertFalse(config.getDbPassword().isBlank());
    }

    /**
     * Tests that the loadFromClasspath method reads the expected keys and ignores comment lines.
     * @throws Exception if there is an error loading the properties from the application.config on the classpath
     */
    @Test
    void loadFromClasspath_reads_expected_keys_and_ignores_comment_lines() throws Exception {
        ApplicationConfig config = new ApplicationConfig();
        Properties props = loadFromClasspath(config);

        assertTrue(
                hasClasspathDbKeys(props) || dbEnvFullySet(),
                "Either application.config on the classpath must define db.url, db.user, and db.password, "
                        + "or DB_URL, DB_USER, and DB_PASSWORD must all be set");

        if (hasClasspathDbKeys(props)) {
            assertNotNull(props.getProperty("db.url"));
            assertNotNull(props.getProperty("db.user"));
            assertNotNull(props.getProperty("db.password"));
            assertNull(props.getProperty("% db.url"));
            assertNull(props.getProperty("# db.url"));
        }
    }

    /**
     * Tests that the envOrProp method returns the trimmed property value when the environment variable is missing.
     * @throws Exception if there is an error loading the properties from the application.config on the classpath
     */
    @Test
    void envOrProp_returns_trimmed_property_value_when_env_missing() throws Exception {
        Method method = ApplicationConfig.class.getDeclaredMethod("envOrProp", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, "__OPENROBOTICS_TEST_ENV_KEY__", "  fallback-value  ");

        assertEquals("fallback-value", result);
    }
}
