package com.nl2sql.plan;

import java.util.Objects;

/**
 * Represents a selected field in a QueryPlan.
 */
public class SelectField {
    private final String tableName;
    private final String columnName;
    private final String alias;
    private final AggregationType aggregationType;

    public SelectField(String tableName, String columnName) {
        this(tableName, columnName, null, AggregationType.NONE);
    }

    public SelectField(String tableName, String columnName, String alias, AggregationType aggregationType) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.alias = alias;
        this.aggregationType = aggregationType != null ? aggregationType : AggregationType.NONE;
    }

    public String getTableName() {
        return tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getAlias() {
        return alias;
    }

    public AggregationType getAggregationType() {
        return aggregationType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SelectField that = (SelectField) o;
        return Objects.equals(tableName, that.tableName) &&
                Objects.equals(columnName, that.columnName) &&
                Objects.equals(alias, that.alias) &&
                aggregationType == that.aggregationType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName, columnName, alias, aggregationType);
    }

    @Override
    public String toString() {
        String colStr = (tableName != null && !tableName.isEmpty() ? tableName + "." : "") + columnName;
        if (aggregationType != AggregationType.NONE) {
            colStr = aggregationType.name() + "(" + colStr + ")";
        }
        if (alias != null && !alias.isEmpty()) {
            colStr += " AS " + alias;
        }
        return colStr;
    }
}
