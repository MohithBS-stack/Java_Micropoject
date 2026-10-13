package com.nl2sql.plan;

import java.util.Objects;

/**
 * Represents a JOIN relationship between two tables in a QueryPlan.
 */
public class JoinCondition {
    private final String leftTable;
    private final String leftColumn;
    private final String rightTable;
    private final String rightColumn;
    private final JoinType joinType;

    public JoinCondition(String leftTable, String leftColumn, String rightTable, String rightColumn, JoinType joinType) {
        this.leftTable = leftTable;
        this.leftColumn = leftColumn;
        this.rightTable = rightTable;
        this.rightColumn = rightColumn;
        this.joinType = joinType != null ? joinType : JoinType.INNER;
    }

    public String getLeftTable() {
        return leftTable;
    }

    public String getLeftColumn() {
        return leftColumn;
    }

    public String getRightTable() {
        return rightTable;
    }

    public String getRightColumn() {
        return rightColumn;
    }

    public JoinType getJoinType() {
        return joinType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JoinCondition that = (JoinCondition) o;
        return Objects.equals(leftTable, that.leftTable) &&
                Objects.equals(leftColumn, that.leftColumn) &&
                Objects.equals(rightTable, that.rightTable) &&
                Objects.equals(rightColumn, that.rightColumn) &&
                joinType == that.joinType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(leftTable, leftColumn, rightTable, rightColumn, joinType);
    }

    @Override
    public String toString() {
        return leftTable + "." + leftColumn + " = " + rightTable + "." + rightColumn + " (" + joinType.name() + ")";
    }
}
