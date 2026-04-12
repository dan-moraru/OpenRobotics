package com.openrobotics.db;

import com.openrobotics.config.ApplicationConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Central database access: runs Flyway migrations at startup and provides a shared HikariCP DataSource.
 * Call {@link #init()} once before using the database (from MainApp.java).
 */
public final class Database {

    private static volatile HikariDataSource dataSource;
    private static volatile boolean shutdown;
    private static volatile boolean uuidRobotSchemaReady;
    private static final boolean ALLOW_SKIP_MIGRATION_DUE_TO_OWNERSHIP =
        Boolean.parseBoolean(System.getProperty("openrobotics.db.allowSkipMigrationDueToOwnership", "false"));

    private Database() {}

    /**
     * Loads config, runs Flyway migrations, and creates the connection pool.
     * Safe to call multiple times; subsequent calls are no-ops after the first successful init.
     * @throws IOException if there is an error reading the config file
     * @throws SQLException if there is an error connecting to the database or running migrations
     */
    public static synchronized void init() throws IOException {
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

        Path devMigrationDir = Path.of("src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
        List<String> migrationLocations = new ArrayList<>(1);
        if (Files.isDirectory(devMigrationDir)) {
            // Prefer filesystem migrations in local/test runs to avoid module-path resource stream issues.
            migrationLocations.add("filesystem:" + devMigrationDir);
        } else {
            migrationLocations.add("classpath:db/migration");
        }

        Flyway flyway = Flyway.configure()
            .dataSource(url, user, password)
            .locations(migrationLocations.toArray(String[]::new))
            .load();
        try {
            flyway.migrate();
        } catch (FlywayException ex) {
            String msg = ex.getMessage();
            boolean ownershipIssue = msg != null && (
                msg.contains("must be owner of table") ||
                msg.contains("must be owner of index")
            );
            if (!ownershipIssue) {
                throw ex;
            }
            if (!ALLOW_SKIP_MIGRATION_DUE_TO_OWNERSHIP) {
                throw ex;
            }
            System.err.println("[Database] Flyway migration skipped due to DB ownership permissions (opt-in enabled). "
                + "Using existing schema as-is. Details: " + msg);
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);

        dataSource = new HikariDataSource(hikariConfig);
        uuidRobotSchemaReady = detectUuidRobotSchema();
        if (!uuidRobotSchemaReady) {
            System.err.println("[Database] UUID robot schema is not active. "
                + "This usually means migration V2 could not be applied with current DB permissions.");
        }
    }

    private static boolean detectUuidRobotSchema() {
        String sql = """
            select data_type
            from information_schema.columns
            where table_schema = current_schema()
              and table_name = ?
              and column_name = ?
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            return isUuidColumn(ps, "run_workload_tasks", "assigned_robot_id")
                && isUuidColumn(ps, "sim_logs", "robot_id")
                && isUuidColumn(ps, "robot_run_stats", "robot_id");
        } catch (SQLException ex) {
            System.err.println("[Database] Failed to inspect schema readiness: " + ex.getMessage());
            return false;
        }
    }

    private static boolean isUuidColumn(PreparedStatement ps, String table, String column) throws SQLException {
        ps.clearParameters();
        ps.setString(1, table);
        ps.setString(2, column);
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() && "uuid".equalsIgnoreCase(rs.getString(1));
        }
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
     * Indicates whether the DB schema has UUID robot identifier columns expected by newer DAO tests.
     */
    public static boolean isUuidRobotSchemaReady() {
        if (dataSource == null) {
            throw new IllegalStateException("Database not initialized. Call Database.init() at startup.");
        }
        if (shutdown) {
            throw new IllegalStateException("Database has been shut down.");
        }
        return uuidRobotSchemaReady;
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
