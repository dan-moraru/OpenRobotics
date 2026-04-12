package com.openrobotics.db;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatabaseLifecycleTest {

    private HikariDataSource originalDataSource;
    private boolean originalShutdown;
    private boolean originalUuidRobotSchemaReady;

    @BeforeEach
    void captureState() throws Exception {
        originalDataSource = (HikariDataSource) getField("dataSource");
        originalShutdown = (boolean) getField("shutdown");
        originalUuidRobotSchemaReady = (boolean) getField("uuidRobotSchemaReady");
    }

    @AfterEach
    void restoreState() throws Exception {
        setField("dataSource", originalDataSource);
        setField("shutdown", originalShutdown);
        setField("uuidRobotSchemaReady", originalUuidRobotSchemaReady);
    }

    @Test
    void getDataSource_throws_when_database_has_not_been_initialized() throws Exception {
        setField("dataSource", null);
        setField("shutdown", false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, Database::getDataSource);

        assertTrue(ex.getMessage().contains("Database not initialized"));
    }

    @Test
    void isUuidRobotSchemaReady_throws_when_database_has_not_been_initialized() throws Exception {
        setField("dataSource", null);
        setField("shutdown", false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, Database::isUuidRobotSchemaReady);

        assertTrue(ex.getMessage().contains("Database not initialized"));
    }

    @Test
    void init_fails_fast_when_database_has_already_been_shut_down() throws Exception {
        setField("dataSource", null);
        setField("shutdown", true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, Database::init);

        assertTrue(ex.getMessage().contains("shut down"));
    }

    @Test
    void shutdown_closes_current_data_source_and_blocks_further_access() throws Exception {
        HikariDataSource dataSource = new HikariDataSource();
        setField("dataSource", dataSource);
        setField("shutdown", false);

        Database.shutdown();

        assertTrue(dataSource.isClosed());
        assertThrows(IllegalStateException.class, Database::getDataSource);
    }

    private Object getField(String fieldName) throws Exception {
        Field field = Database.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = Database.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
