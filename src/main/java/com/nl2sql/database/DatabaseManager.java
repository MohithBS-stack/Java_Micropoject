package com.nl2sql.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Generic Database Manager for SQLite connections.
 * Handles JDBC connection lifecycle, path resolution, and resource safety.
 */
public class DatabaseManager implements AutoCloseable {

    private static final String DEFAULT_DB_DIR = "data";
    private static final String DEFAULT_DB_NAME = "pipeline_database.db";

    private final String dbPath;
    private final String jdbcUrl;
    private Connection connection;

    /**
     * Initializes DatabaseManager with default relative file path ("data/pipeline_database.db").
     */
    public DatabaseManager() {
        this(DEFAULT_DB_DIR + File.separator + DEFAULT_DB_NAME);
    }

    /**
     * Initializes DatabaseManager with a specific relative or absolute database file path.
     *
     * @param dbPath Path to SQLite database file
     */
    public DatabaseManager(String dbPath) {
        if (dbPath != null && dbPath.startsWith("jdbc:")) {
            this.dbPath = dbPath;
            this.jdbcUrl = dbPath;
        } else {
            this.dbPath = dbPath;
            this.jdbcUrl = "jdbc:sqlite:" + dbPath;
            ensureDirectoryExists();
        }
    }

    /**
     * Ensures parent directory of the database file exists.
     */
    private void ensureDirectoryExists() {
        if (dbPath == null) return;
        File dbFile = new File(dbPath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }

    /**
     * Obtains or reopens an active JDBC Connection.
     * Enables foreign key enforcement on SQLite connections.
     *
     * @return Active Connection instance
     * @throws SQLException If database connection fails
     */
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl);
            if (jdbcUrl.startsWith("jdbc:sqlite:")) {
                enableForeignKeys(connection);
            }
        }
        return connection;
    }

    /**
     * Enables foreign key constraints in SQLite.
     */
    private void enableForeignKeys(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    /**
     * Gets the current database file path.
     */
    public String getDbPath() {
        return dbPath;
    }

    /**
     * Closes the underlying database connection gracefully.
     */
    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println("Warning: Error closing database connection: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }
}
