package com.nl2sql.plan;

import java.util.Objects;

/**
 * Represents a WHERE filter condition in a QueryPlan.
 */
public class FilterCondition {
    private final String tableName;
    private final String columnName;
    private final Operator operator;
    private final Object value;

    public FilterCondition(String tableName, String columnName, Operator operator, Object value) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.operator = operator != null ? operator : Operator.EQUALS;
        this.value = value;
    }

    public String getTableName() {
        return tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public Operator getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilterCondition that = (FilterCondition) o;
        return Objects.equals(tableName, that.tableName) &&
                Objects.equals(columnName, that.columnName) &&
                operator == that.operator &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName, columnName, operator, value);
    }

    @Override
    public String toString() {
        String valStr = value instanceof String ? "'" + value + "'" : String.valueOf(value);
        return (tableName != null && !tableName.isEmpty() ? tableName + "." : "") + columnName + " " + operator.getSymbol() + " " + valStr;
    }
}
