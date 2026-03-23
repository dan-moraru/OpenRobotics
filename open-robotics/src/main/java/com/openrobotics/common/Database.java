package com.openrobotics.common;

import com.openrobotics.config.ApplicationConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Central database access: runs Flyway migrations at startup and provides a shared HikariCP DataSource.
 * Call {@link #init()} once before using the database (from MainApp.java).
 */
public final class Database {

    private static volatile HikariDataSource dataSource;
    private static volatile boolean shutdown;

    private Database() {}

    /**
     * Loads config, runs Flyway migrations, and creates the connection pool.
     * Safe to call multiple times; subsequent calls are no-ops after the first successful init.
     * @throws IOException if there is an error reading the config file
     * @throws SQLException if there is an error connecting to the database or running migrations
     */
    public static synchronized void init() throws IOException, SQLException {
        if (shutdown) {
            throw new IllegalStateException("Database has been shut down and cannot be re-initialized in this process.");
        }
        if (dataSource != null) {
            return;
        }
        ApplicationConfig config = new ApplicationConfig();
        String url = config.getDbUrl();
        String user = config.getDbUser();
        String password = config.getDbPassword();

        Flyway flyway = Flyway.configure()
            .dataSource(url, user, password)
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);

        dataSource = new HikariDataSource(hikariConfig);
    }

    /**
     * Returns the shared DataSource. {@link #init()} must have been called first.
     * @throws IllegalStateException if the database has not been initialized yet
     */
    public static DataSource getDataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("Database not initialized. Call Database.init() at startup.");
        }
        if (shutdown) {
            throw new IllegalStateException("Database has been shut down.");
        }
        return dataSource;
    }

    /**
     * Returns a connection from the pool. Caller must close it (e.g. try-with-resources).
     * @throws IllegalStateException if the database has not been initialized yet
     */
    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    /**
     * Closes the shared Hikari pool and marks the DB layer as shut down.
     */
    public static synchronized void shutdown() {
        HikariDataSource ds = dataSource;
        dataSource = null;
        shutdown = true;
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }
}
