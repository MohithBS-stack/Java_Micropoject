package com.nl2sql.database;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Generic Database Initializer wrapper.
 * Delegates schema initialization and placeholder seeding to a domain-specific SchemaInitializer strategy.
 */
public class DatabaseInitializer {

    private final DatabaseManager databaseManager;
    private final SchemaInitializer schemaInitializer;

    public DatabaseInitializer(DatabaseManager databaseManager, SchemaInitializer schemaInitializer) {
        this.databaseManager = databaseManager;
        this.schemaInitializer = schemaInitializer;
    }

    public void initializeSchema() throws SQLException {
        if (schemaInitializer != null) {
            schemaInitializer.initializeSchema(databaseManager);
        }
    }

    public List<String> getExistingTables() throws SQLException {
        if (schemaInitializer != null) {
            return schemaInitializer.getExistingTables(databaseManager);
        }
        return Collections.emptyList();
    }

    public void seedPlaceholdersIfEmpty() throws SQLException {
        if (schemaInitializer != null) {
            schemaInitializer.seedPlaceholdersIfEmpty(databaseManager);
        }
    }
}
