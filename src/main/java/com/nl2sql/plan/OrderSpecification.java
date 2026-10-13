package com.nl2sql.plan;

import java.util.Objects;

/**
 * Represents an ORDER BY clause in a QueryPlan.
 */
public class OrderSpecification {
    private final String tableName;
    private final String columnName;
    private final boolean ascending;

    public OrderSpecification(String tableName, String columnName, boolean ascending) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.ascending = ascending;
    }

    public String getTableName() {
        return tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public boolean isAscending() {
        return ascending;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderSpecification that = (OrderSpecification) o;
        return ascending == that.ascending &&
                Objects.equals(tableName, that.tableName) &&
                Objects.equals(columnName, that.columnName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName, columnName, ascending);
    }

    @Override
    public String toString() {
        return (tableName != null && !tableName.isEmpty() ? tableName + "." : "") + columnName + " " + (ascending ? "ASC" : "DESC");
    }
}
