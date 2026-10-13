package com.nl2sql.database;

import java.sql.SQLException;
import java.util.List;

/**
 * Interface defining domain-specific database schema initialization and placeholder seeding.
 */
public interface SchemaInitializer {

    /**
     * Initializes database tables and schema DDL statements.
     *
     * @param databaseManager Active DatabaseManager instance
     * @throws SQLException If DDL execution fails
     */
    void initializeSchema(DatabaseManager databaseManager) throws SQLException;

    /**
     * Seeds initial non-chemical placeholder records if tables are empty.
     *
     * @param databaseManager Active DatabaseManager instance
     * @throws SQLException If DML insert operations fail
     */
    void seedPlaceholdersIfEmpty(DatabaseManager databaseManager) throws SQLException;

    /**
     * Inspects active catalog to verify schema tables.
     *
     * @param databaseManager Active DatabaseManager instance
     * @return List of table names present in the catalog
     * @throws SQLException If database metadata query fails
     */
    List<String> getExistingTables(DatabaseManager databaseManager) throws SQLException;
}
