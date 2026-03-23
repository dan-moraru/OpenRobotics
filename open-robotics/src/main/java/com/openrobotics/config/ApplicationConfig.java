package com.openrobotics.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Loads database configuration from application.config.
 * Environment variables DB_URL, DB_USER, DB_PASSWORD override file values when set.
 */
public class ApplicationConfig {

    private static final String CONFIG_RESOURCE = "application.config";

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    /**
     * Loads config from application.config on the classpath, then overrides with environment variables if set.
     * @throws IOException if there is an error reading the config file
     */
    public ApplicationConfig() throws IOException {
        Properties props = loadFromClasspath();
        dbUrl = envOrProp("DB_URL", props.getProperty("db.url"));
        dbUser = envOrProp("DB_USER", props.getProperty("db.user"));
        dbPassword = envOrProp("DB_PASSWORD", props.getProperty("db.password"));
        if (isBlank(dbUrl) || isBlank(dbUser) || isBlank(dbPassword)) {
            throw new IllegalStateException(
                "Missing database config. Set db.url, db.user, db.password in " + CONFIG_RESOURCE
                    + " or DB_URL, DB_USER, DB_PASSWORD environment variables.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Returns the environment variable value if set and non-blank, otherwise returns the property value.
     * @param envKey the name of the environment variable to check
     * @param propValue the property value to use if the environment variable is not set
     * @return the environment variable value if set and non-blank, otherwise the property value (which may be null)
     */
    private static String envOrProp(String envKey, String propValue) {
        String env = System.getenv(envKey);
        return env != null && !env.isBlank() ? env.trim() : (propValue != null ? propValue.trim() : null);
    }

    /**
     * Loads properties from application.config on the classpath. Lines starting with # are comments and ignored.
     * @return a Properties object containing the key-value pairs from the config file, or empty if the file is not found
     * @throws IOException if there is an error reading the config file
     */
    private Properties loadFromClasspath() throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_RESOURCE)) {
            if (in == null) {
                return props;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("%")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String key = line.substring(0, eq).trim();
                        String value = line.substring(eq + 1).trim();
                        props.setProperty(key, value);
                    }
                }
            }
        }
        return props;
    }

    /**
     * Returns the database URL to connect to, e.g. "jdbc:postgresql://localhost:5432/mydb".
     * @return the database URL, never null
     */
    public String getDbUrl() {
        return dbUrl;
    }

    /**
     * Returns the database username to connect with.
     * @return the database username, never null
     */
    public String getDbUser() {
        return dbUser;
    }

    /**
     * Returns the database password to connect with.
     * @return the database password, never null
     */
    public String getDbPassword() {
        return dbPassword;
    }
}
