package com.nl2sql.metadata;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents metadata for a database table, including its columns and foreign key relations.
 */
public class TableMetadata {
    private final String tableName;
    private final List<ColumnMetadata> columns;
    private final List<ForeignKeyMetadata> foreignKeys;
    private final Map<String, ColumnMetadata> columnMap;

    public TableMetadata(String tableName, List<ColumnMetadata> columns, List<ForeignKeyMetadata> foreignKeys, Map<String, ColumnMetadata> columnMap) {
        this.tableName = tableName;
        this.columns = Collections.unmodifiableList(columns);
        this.foreignKeys = Collections.unmodifiableList(foreignKeys);
        this.columnMap = Collections.unmodifiableMap(columnMap);
    }

    public String getTableName() {
        return tableName;
    }

    public List<ColumnMetadata> getColumns() {
        return columns;
    }

    public List<ForeignKeyMetadata> getForeignKeys() {
        return foreignKeys;
    }

    public boolean hasColumn(String columnName) {
        return columnName != null && columnMap.containsKey(columnName.toLowerCase());
    }

    public ColumnMetadata getColumn(String columnName) {
        return columnName != null ? columnMap.get(columnName.toLowerCase()) : null;
    }

    /**
     * Returns list of columns that are part of the table's primary key.
     *
     * @return List of primary key ColumnMetadata instances
     */
    public List<ColumnMetadata> getPrimaryKeyColumns() {
        return columns.stream().filter(ColumnMetadata::isPrimaryKey).toList();
    }

    /**
     * Checks if the table has a composite primary key (more than one primary key column).
     *
     * @return true if primary key consists of two or more columns
     */
    public boolean hasCompositePrimaryKey() {
        return getPrimaryKeyColumns().size() > 1;
    }
}
