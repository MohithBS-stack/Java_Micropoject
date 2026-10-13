package com.nl2sql.metadata;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates full database schema metadata discovered dynamically from SQLite.
 */
public class SchemaMetadata {
    private final Map<String, TableMetadata> tables;

    public SchemaMetadata(Map<String, TableMetadata> tables) {
        this.tables = Collections.unmodifiableMap(tables);
    }

    public Map<String, TableMetadata> getTables() {
        return tables;
    }

    public boolean hasTable(String tableName) {
        return tableName != null && tables.containsKey(tableName.toLowerCase());
    }

    public TableMetadata getTable(String tableName) {
        return tableName != null ? tables.get(tableName.toLowerCase()) : null;
    }

    public boolean hasColumn(String tableName, String columnName) {
        TableMetadata table = getTable(tableName);
        return table != null && table.hasColumn(columnName);
    }

    public List<ForeignKeyMetadata> getRelationships(String tableName) {
        TableMetadata table = getTable(tableName);
        return table != null ? table.getForeignKeys() : Collections.emptyList();
    }

    /**
     * Returns a clean, human-readable string representation of the database schema.
     */
    public String getFormattedSchema() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DISCOVERED DATABASE SCHEMA METADATA ===\n");
        for (TableMetadata table : tables.values()) {
            sb.append("\nTABLE: ").append(table.getTableName()).append("\n");
            for (ColumnMetadata col : table.getColumns()) {
                sb.append("  ").append(col.toString()).append("\n");
            }
            for (ForeignKeyMetadata fk : table.getForeignKeys()) {
                sb.append("  ").append(fk.toString()).append("\n");
            }
        }
        sb.append("\n===========================================");
        return sb.toString();
    }
}
