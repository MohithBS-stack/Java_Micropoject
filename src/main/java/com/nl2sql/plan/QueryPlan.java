package com.nl2sql.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generic, domain-independent intermediate representation of a natural-language query.
 * Encapsulates targets, fields, filters, joins, aggregations, ordering, and status.
 */
public class QueryPlan {
    private final String question;
    private final QueryPlanStatus status;
    private final String statusReason;
    private final QueryIntent intent;
    private final List<String> targetTables;
    private final List<SelectField> selectFields;
    private final List<FilterCondition> filterConditions;
    private final List<JoinCondition> joinConditions;
    private final List<SelectField> groupByFields;
    private final List<HavingCondition> havingConditions;
    private final List<OrderSpecification> orderSpecifications;
    private final Integer limit;
    private final boolean distinct;
    private final List<Object> parameters;

    private QueryPlan(Builder builder) {
        this.question = builder.question;
        this.status = builder.status;
        this.statusReason = builder.statusReason;
        this.intent = builder.intent;
        this.targetTables = Collections.unmodifiableList(new ArrayList<>(builder.targetTables));
        this.selectFields = Collections.unmodifiableList(new ArrayList<>(builder.selectFields));
        this.filterConditions = Collections.unmodifiableList(new ArrayList<>(builder.filterConditions));
        this.joinConditions = Collections.unmodifiableList(new ArrayList<>(builder.joinConditions));
        this.groupByFields = Collections.unmodifiableList(new ArrayList<>(builder.groupByFields));
        this.havingConditions = Collections.unmodifiableList(new ArrayList<>(builder.havingConditions));
        this.orderSpecifications = Collections.unmodifiableList(new ArrayList<>(builder.orderSpecifications));
        this.limit = builder.limit;
        this.distinct = builder.distinct;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(builder.parameters));
    }

    public String getQuestion() { return question; }
    public QueryPlanStatus getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public QueryIntent getIntent() { return intent; }
    public List<String> getTargetTables() { return targetTables; }
    public List<SelectField> getSelectFields() { return selectFields; }
    public List<FilterCondition> getFilterConditions() { return filterConditions; }
    public List<JoinCondition> getJoinConditions() { return joinConditions; }
    public List<SelectField> getGroupByFields() { return groupByFields; }
    public List<HavingCondition> getHavingConditions() { return havingConditions; }
    public List<OrderSpecification> getOrderSpecifications() { return orderSpecifications; }
    public Integer getLimit() { return limit; }
    public boolean isDistinct() { return distinct; }
    public List<Object> getParameters() { return parameters; }

    public static QueryPlan unsupported(String question, String reason) {
        return new Builder(question)
                .status(QueryPlanStatus.UNSUPPORTED)
                .statusReason(reason)
                .intent(QueryIntent.UNKNOWN)
                .build();
    }

    public static QueryPlan ambiguous(String question, String reason) {
        return new Builder(question)
                .status(QueryPlanStatus.AMBIGUOUS)
                .statusReason(reason)
                .intent(QueryIntent.UNKNOWN)
                .build();
    }

    public String toFormattedPlan() {
        StringBuilder sb = new StringBuilder();
        sb.append("QUESTION:\n").append(question).append("\n\n");
        sb.append("STATUS:\n").append(status);
        if (statusReason != null && !statusReason.trim().isEmpty()) {
            sb.append(" (").append(statusReason).append(")");
        }
        sb.append("\n\n");
        if (status == QueryPlanStatus.SUPPORTED) {
            sb.append("INTENT:\n").append(intent).append("\n\n");
            sb.append("TABLES:\n").append(targetTables.isEmpty() ? "none" : String.join(", ", targetTables)).append("\n\n");
            sb.append("SELECT:\n");
            if (selectFields.isEmpty()) {
                sb.append("none\n\n");
            } else {
                for (SelectField sf : selectFields) {
                    sb.append("  ").append(sf.toString()).append("\n");
                }
                sb.append("\n");
            }
            sb.append("FILTERS:\n");
            if (filterConditions.isEmpty()) {
                sb.append("none\n\n");
            } else {
                for (FilterCondition fc : filterConditions) {
                    sb.append("  ").append(fc.toString()).append("\n");
                }
                sb.append("\n");
            }
            sb.append("JOINS:\n");
            if (joinConditions.isEmpty()) {
                sb.append("none\n\n");
            } else {
                for (JoinCondition jc : joinConditions) {
                    sb.append("  ").append(jc.toString()).append("\n");
                }
                sb.append("\n");
            }
            sb.append("GROUP BY:\n");
            if (groupByFields.isEmpty()) {
                sb.append("none\n\n");
            } else {
                for (SelectField gf : groupByFields) {
                    sb.append("  ").append(gf.toString()).append("\n");
                }
                sb.append("\n");
            }
            sb.append("ORDER BY:\n");
            if (orderSpecifications.isEmpty()) {
                sb.append("none\n\n");
            } else {
                for (OrderSpecification os : orderSpecifications) {
                    sb.append("  ").append(os.toString()).append("\n");
                }
                sb.append("\n");
            }
            sb.append("LIMIT:\n").append(limit != null ? limit : "none").append("\n");
        }
        return sb.toString();
    }

    public static class Builder {
        private final String question;
        private QueryPlanStatus status = QueryPlanStatus.SUPPORTED;
        private String statusReason;
        private QueryIntent intent = QueryIntent.SELECT;
        private final List<String> targetTables = new ArrayList<>();
        private final List<SelectField> selectFields = new ArrayList<>();
        private final List<FilterCondition> filterConditions = new ArrayList<>();
        private final List<JoinCondition> joinConditions = new ArrayList<>();
        private final List<SelectField> groupByFields = new ArrayList<>();
        private final List<HavingCondition> havingConditions = new ArrayList<>();
        private final List<OrderSpecification> orderSpecifications = new ArrayList<>();
        private Integer limit;
        private boolean distinct;
        private final List<Object> parameters = new ArrayList<>();

        public Builder(String question) {
            this.question = question;
        }

        public Builder status(QueryPlanStatus status) { this.status = status; return this; }
        public Builder statusReason(String statusReason) { this.statusReason = statusReason; return this; }
        public Builder intent(QueryIntent intent) { this.intent = intent; return this; }
        public Builder addTargetTable(String table) {
            if (table != null && !targetTables.contains(table)) {
                targetTables.add(table);
            }
            return this;
        }
        public Builder addSelectField(SelectField field) { selectFields.add(field); return this; }
        public Builder addFilterCondition(FilterCondition condition) {
            filterConditions.add(condition);
            if (condition.getValue() != null) {
                parameters.add(condition.getValue());
            }
            return this;
        }
        public Builder addJoinCondition(JoinCondition join) { joinConditions.add(join); return this; }
        public Builder addGroupByField(SelectField field) { groupByFields.add(field); return this; }
        public Builder addHavingCondition(HavingCondition condition) {
            havingConditions.add(condition);
            if (condition.getValue() != null) {
                parameters.add(condition.getValue());
            }
            return this;
        }
        public Builder addOrderSpecification(OrderSpecification order) { orderSpecifications.add(order); return this; }
        public Builder limit(Integer limit) { this.limit = limit; return this; }
        public Builder distinct(boolean distinct) { this.distinct = distinct; return this; }

        public QueryPlan build() {
            return new QueryPlan(this);
        }
    }
}
