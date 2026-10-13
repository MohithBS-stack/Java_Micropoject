package com.nl2sql.plan;

/**
 * Represents a HAVING clause condition in an intermediate QueryPlan.
 * Evaluates an aggregation on a column/expression against an operator and value.
 */
public class HavingCondition {

    private final String tableName;
    private final String columnName;
    private final AggregationType aggregationType;
    private final Operator operator;
    private final Object value;

    public HavingCondition(String tableName, String columnName, AggregationType aggregationType, Operator operator, Object value) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.aggregationType = aggregationType != null ? aggregationType : AggregationType.COUNT;
        this.operator = operator != null ? operator : Operator.GREATER_THAN;
        this.value = value;
    }

    public String getTableName() { return tableName; }
    public String getColumnName() { return columnName; }
    public AggregationType getAggregationType() { return aggregationType; }
    public Operator getOperator() { return operator; }
    public Object getValue() { return value; }

    @Override
    public String toString() {
        return aggregationType.name() + "(" + (tableName != null ? tableName + "." : "") + columnName + ") " +
                operator.getSymbol() + " " + (value != null ? value.toString() : "NULL");
    }
}
