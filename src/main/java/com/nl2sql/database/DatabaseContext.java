package com.nl2sql.database;

import com.nl2sql.metadata.SchemaInspector;
import com.nl2sql.metadata.SchemaMetadata;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Generic Active Database Context representing a selected SQLite database.
 * Encapsulates the database path, JDBC URL, underlying DatabaseManager connection pool,
 * and automatically discovered SchemaMetadata.
 */
public class DatabaseContext implements AutoCloseable {

    private final String dbPath;
    private final String jdbcUrl;
    private final DatabaseManager databaseManager;
    private final SchemaMetadata schemaMetadata;

    /**
     * Constructs a DatabaseContext with an explicit path, DatabaseManager, and SchemaMetadata.
     *
     * @param dbPath          File path to the SQLite database
     * @param databaseManager Active DatabaseManager instance
     * @param schemaMetadata  Discovered SchemaMetadata graph
     */
    public DatabaseContext(String dbPath, DatabaseManager databaseManager, SchemaMetadata schemaMetadata) {
        if (dbPath == null || dbPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Database path cannot be null or empty.");
        }
        this.dbPath = dbPath;
        this.jdbcUrl = dbPath.startsWith("jdbc:") ? dbPath : ("jdbc:sqlite:" + dbPath);
        this.databaseManager = Objects.requireNonNull(databaseManager, "DatabaseManager cannot be null");
        this.schemaMetadata = schemaMetadata;
    }

    /**
     * Opens an arbitrary SQLite database path and automatically discovers its SchemaMetadata.
     *
     * @param dbPath Path to the SQLite database file
     * @return Initialized DatabaseContext instance
     * @throws SQLException If database opening or schema inspection fails
     */
    public static DatabaseContext open(String dbPath) throws SQLException {
        if (dbPath == null || dbPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Database path cannot be null or empty.");
        }
        DatabaseManager manager = new DatabaseManager(dbPath);
        SchemaInspector inspector = new SchemaInspector(manager);
        SchemaMetadata metadata = inspector.inspectSchema();
        return new DatabaseContext(dbPath, manager, metadata);
    }

    /**
     * Gets the database file path.
     *
     * @return Database file path
     */
    public String getDbPath() {
        return dbPath;
    }

    /**
     * Gets the JDBC connection URL.
     *
     * @return JDBC URL string
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Gets the underlying DatabaseManager instance.
     *
     * @return Active DatabaseManager
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Obtains an active JDBC Connection from the context's DatabaseManager.
     *
     * @return JDBC Connection
     * @throws SQLException If obtaining connection fails
     */
    public Connection getConnection() throws SQLException {
        return databaseManager.getConnection();
    }

    /**
     * Gets the discovered SchemaMetadata.
     *
     * @return Discovered SchemaMetadata
     */
    public SchemaMetadata getSchemaMetadata() {
        return schemaMetadata;
    }

    /**
     * Re-inspects the active database schema catalog dynamically and updates metadata.
     *
     * @return Refreshed SchemaMetadata instance
     * @throws SQLException If metadata discovery fails
     */
    public SchemaMetadata refreshSchema() throws SQLException {
        SchemaInspector inspector = new SchemaInspector(databaseManager);
        return inspector.inspectSchema();
    }

    /**
     * Closes the database context and releases underlying database connection resources.
     */
    @Override
    public void close() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    @Override
    public String toString() {
        return "DatabaseContext{" +
                "dbPath='" + dbPath + '\'' +
                ", jdbcUrl='" + jdbcUrl + '\'' +
                ", tablesDiscovered=" + (schemaMetadata != null ? schemaMetadata.getTables().size() : 0) +
                '}';
    }
}
