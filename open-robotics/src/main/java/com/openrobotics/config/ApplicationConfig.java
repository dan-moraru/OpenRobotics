package com.openrobotics.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Loads database configuration from application.config; env vars DB_URL, DB_USER, and DB_PASSWORD override file values. */
public class ApplicationConfig {

    private static final String CONFIG_RESOURCE = "application.config";

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    /**
     * Loads config from application.config on the classpath, then overrides with env vars if set.
     *
     * @throws IOException if the config file cannot be read
     * @throws IllegalStateException if any required database config value is missing after loading
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

    // returns env var value if set and non-blank, otherwise falls back to the property value
    private static String envOrProp(String envKey, String propValue) {
        String env = System.getenv(envKey);
        return env != null && !env.isBlank() ? env.trim() : (propValue != null ? propValue.trim() : null);
    }

    // reads application.config from the classpath; lines starting with # or % are treated as comments
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

    public String getDbUrl() {
        return dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }
}
