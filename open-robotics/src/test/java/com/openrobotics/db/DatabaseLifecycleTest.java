package com.openrobotics.db;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reflection-backed lifecycle tests for {@link Database}'s static state machine.
 *
 * <p>This suite validates failure and guard behavior around initialization, shutdown, and access
 * APIs without requiring a real DB connection. It snapshots/restores static internals before and
 * after each test to keep behavior deterministic and avoid cross-test pollution.</p>
 */
public class DatabaseLifecycleTest {

    private HikariDataSource originalDataSource;
    private boolean originalShutdown;
    private boolean originalUuidRobotSchemaReady;

    /**
     * Captures current static {@link Database} internals so each test can safely mutate them.
     *
     * @throws Exception if reflective field access fails
     */
    @BeforeEach
    void captureState() throws Exception {
        originalDataSource = (HikariDataSource) getField("dataSource");
        originalShutdown = (boolean) getField("shutdown");
        originalUuidRobotSchemaReady = (boolean) getField("uuidRobotSchemaReady");
    }

    /**
     * Restores static {@link Database} internals to their pre-test values.
     *
     * @throws Exception if reflective field write fails
     */
    @AfterEach
    void restoreState() throws Exception {
        setField("dataSource", originalDataSource);
        setField("shutdown", originalShutdown);
        setField("uuidRobotSchemaReady", originalUuidRobotSchemaReady);
    }

    /**
     * Verifies {@link Database#getDataSource()} rejects access before successful initialization.
     *
     * @throws Exception if reflective state setup fails
     */
    @Test
    void getDataSource_throws_when_database_has_not_been_initialized() throws Exception {
        setField("dataSource", null);
        setField("shutdown", false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, Database::getDataSource);

        assertTrue(ex.getMessage().contains("Database not initialized"));
    }

    /**
     * Verifies UUID-schema readiness accessor also rejects access before initialization.
     *
     * @throws Exception if reflective state setup fails
     */
    @Test
    void isUuidRobotSchemaReady_throws_when_database_has_not_been_initialized() throws Exception {
        setField("dataSource", null);
        setField("shutdown", false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, Database::isUuidRobotSchemaReady);

        assertTrue(ex.getMessage().contains("Database not initialized"));
    }

    /**
     * Verifies {@link Database#init()} fails fast once shutdown flag is set.
     *
     * @throws Exception if reflective state setup fails
     */
    @Test
    void init_fails_fast_when_database_has_already_been_shut_down() throws Exception {
        setField("dataSource", null);
        setField("shutdown", true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, Database::init);

        assertTrue(ex.getMessage().contains("shut down"));
    }

    /**
     * Verifies {@link Database#shutdown()} closes active datasource and blocks later access.
     *
     * @throws Exception if reflective state setup fails
     */
    @Test
    void shutdown_closes_current_data_source_and_blocks_further_access() throws Exception {
        HikariDataSource dataSource = new HikariDataSource();
        setField("dataSource", dataSource);
        setField("shutdown", false);

        Database.shutdown();

        assertTrue(dataSource.isClosed());
        assertThrows(IllegalStateException.class, Database::getDataSource);
    }

    /**
     * Reads a private static field from {@link Database}.
     *
     * @param fieldName declared field name on {@link Database}
     * @return current field value
     * @throws Exception if reflection lookup/read fails
     */
    private Object getField(String fieldName) throws Exception {
        Field field = Database.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    /**
     * Writes a private static field on {@link Database}.
     *
     * @param fieldName declared field name on {@link Database}
     * @param value value to assign
     * @throws Exception if reflection lookup/write fails
     */
    private void setField(String fieldName, Object value) throws Exception {
        Field field = Database.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
