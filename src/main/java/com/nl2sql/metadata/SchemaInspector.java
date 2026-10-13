package com.nl2sql.metadata;

import com.nl2sql.database.DatabaseManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Dynamically inspects an active SQLite database using JDBC metadata facilities.
 * Completely generic and free of hard-coded table names or chemistry-specific domain logic.
 */
public class SchemaInspector {

    private final DatabaseManager databaseManager;

    public SchemaInspector(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Inspects the database and constructs a complete SchemaMetadata graph directly from SQLite catalog.
     *
     * @return Discovered SchemaMetadata instance
     * @throws SQLException If database metadata reading fails
     */
    public SchemaMetadata inspectSchema() throws SQLException {
        Connection conn = databaseManager.getConnection();
        DatabaseMetaData metaData = conn.getMetaData();
        Map<String, TableMetadata> tableMap = new LinkedHashMap<>();

        // Discover all user tables in SQLite database catalog
        try (ResultSet rsTables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rsTables.next()) {
                String tableName = rsTables.getString("TABLE_NAME");
                if (tableName != null && tableName.toLowerCase().startsWith("sqlite_")) {
                    continue; // Exclude internal SQLite metadata tables
                }

                // Discover Primary Keys for table
                Set<String> primaryKeyCols = new HashSet<>();
                try (ResultSet rsPK = metaData.getPrimaryKeys(null, null, tableName)) {
                    while (rsPK.next()) {
                        String pkCol = rsPK.getString("COLUMN_NAME");
                        if (pkCol != null) {
                            primaryKeyCols.add(pkCol.toLowerCase());
                        }
                    }
                }

                // Discover Columns for table
                List<ColumnMetadata> columns = new ArrayList<>();
                Map<String, ColumnMetadata> columnMap = new LinkedHashMap<>();
                try (ResultSet rsCols = metaData.getColumns(null, null, tableName, null)) {
                    while (rsCols.next()) {
                        String colName = rsCols.getString("COLUMN_NAME");
                        String dataType = rsCols.getString("TYPE_NAME");
                        String isNullableStr = rsCols.getString("IS_NULLABLE");
                        boolean isNullable = "YES".equalsIgnoreCase(isNullableStr);
                        boolean isPk = colName != null && primaryKeyCols.contains(colName.toLowerCase());

                        ColumnMetadata colMeta = new ColumnMetadata(colName, dataType, isPk, isNullable);
                        columns.add(colMeta);
                        if (colName != null) {
                            columnMap.put(colName.toLowerCase(), colMeta);
                        }
                    }
                }

                // Discover Foreign Keys for table
                List<ForeignKeyMetadata> foreignKeys = new ArrayList<>();
                try (ResultSet rsFK = metaData.getImportedKeys(null, null, tableName)) {
                    while (rsFK.next()) {
                        String fkCol = rsFK.getString("FKCOLUMN_NAME");
                        String pkTable = rsFK.getString("PKTABLE_NAME");
                        String pkCol = rsFK.getString("PKCOLUMN_NAME");

                        foreignKeys.add(new ForeignKeyMetadata(fkCol, pkTable, pkCol));
                    }
                }

                TableMetadata tableMeta = new TableMetadata(tableName, columns, foreignKeys, columnMap);
                tableMap.put(tableName.toLowerCase(), tableMeta);
            }
        }

        return new SchemaMetadata(tableMap);
    }
}
