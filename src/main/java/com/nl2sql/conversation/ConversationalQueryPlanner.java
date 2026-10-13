package com.nl2sql.conversation;

import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.plan.*;

/**
 * Decorator around QuestionUnderstandingEngine that resolves multi-turn conversational context.
 * Enables follow-up queries like "sort them by salary descending", "what is their average salary?",
 * or "filter by salary > 80000".
 */
public class ConversationalQueryPlanner implements QuestionUnderstandingEngine {

    private final QuestionUnderstandingEngine delegate;
    private final ConversationSession session;

    public ConversationalQueryPlanner(QuestionUnderstandingEngine delegate) {
        this(delegate, new ConversationSession());
    }

    public ConversationalQueryPlanner(QuestionUnderstandingEngine delegate, ConversationSession session) {
        this.delegate = delegate;
        this.session = session != null ? session : new ConversationSession();
    }

    @Override
    public QueryPlan understand(String question, SchemaMetadata schemaMetadata) {
        if (question == null || question.trim().isEmpty()) {
            return QueryPlan.unsupported("", "Empty question");
        }

        // If this is a follow-up and we have prior context
        if (session.isFollowUp(question) && session.getLastSupportedPlan() != null) {
            QueryPlan priorPlan = session.getLastSupportedPlan();
            QueryPlan resolvedPlan = resolveFollowUp(question, priorPlan, schemaMetadata);
            if (resolvedPlan != null && resolvedPlan.getStatus() == QueryPlanStatus.SUPPORTED) {
                return resolvedPlan;
            }
        }

        // Standard understanding via delegate
        QueryPlan plan = delegate.understand(question, schemaMetadata);
        return plan;
    }

    private QueryPlan resolveFollowUp(String question, QueryPlan priorPlan, SchemaMetadata schemaMetadata) {
        String lower = question.toLowerCase();

        // 1. Check if follow-up is an aggregation: "what is their average salary?", "how many of them?"
        if (lower.contains("average") || lower.contains("avg") || lower.contains("how many") || lower.contains("count")) {
            boolean isCount = lower.contains("how many") || lower.contains("count");
            AggregationType aggType = isCount ? AggregationType.COUNT : AggregationType.AVG;

            QueryPlan.Builder builder = new QueryPlan.Builder(question)
                    .status(QueryPlanStatus.SUPPORTED)
                    .intent(isCount ? QueryIntent.COUNT : QueryIntent.SELECT);

            for (String tbl : priorPlan.getTargetTables()) {
                builder.addTargetTable(tbl);
            }
            for (JoinCondition jc : priorPlan.getJoinConditions()) {
                builder.addJoinCondition(jc);
            }
            for (FilterCondition fc : priorPlan.getFilterConditions()) {
                builder.addFilterCondition(fc);
            }

            String primaryTable = priorPlan.getTargetTables().get(0);
            if (isCount) {
                builder.addSelectField(new SelectField(primaryTable, "*", "total_count", AggregationType.COUNT));
            } else {
                // Find column mentioned in question (e.g. "salary")
                String matchedCol = findReferencedColumn(lower, primaryTable, schemaMetadata);
                if (matchedCol != null) {
                    builder.addSelectField(new SelectField(primaryTable, matchedCol, "avg_" + matchedCol, aggType));
                } else {
                    return null; // Fallback to delegate
                }
            }
            return builder.build();
        }

        // 2. Check if follow-up is ordering: "sort them by salary descending", "order by hire date"
        if (lower.contains("sort") || lower.contains("order")) {
            boolean asc = !lower.contains("desc") && !lower.contains("descending");
            String primaryTable = priorPlan.getTargetTables().get(0);
            String matchedCol = findReferencedColumn(lower, primaryTable, schemaMetadata);

            if (matchedCol != null) {
                QueryPlan.Builder builder = new QueryPlan.Builder(question)
                        .status(QueryPlanStatus.SUPPORTED)
                        .intent(priorPlan.getIntent());

                for (String tbl : priorPlan.getTargetTables()) {
                    builder.addTargetTable(tbl);
                }
                for (SelectField sf : priorPlan.getSelectFields()) {
                    builder.addSelectField(sf);
                }
                for (JoinCondition jc : priorPlan.getJoinConditions()) {
                    builder.addJoinCondition(jc);
                }
                for (FilterCondition fc : priorPlan.getFilterConditions()) {
                    builder.addFilterCondition(fc);
                }
                builder.addOrderSpecification(new OrderSpecification(primaryTable, matchedCol, asc));
                return builder.build();
            }
        }

        // 3. Check if follow-up is an additional filter: "filter by salary > 80000", "only those with salary > 80000"
        QueryPlan deltaPlan = delegate.understand(question, schemaMetadata);
        if (deltaPlan.getStatus() == QueryPlanStatus.SUPPORTED && !deltaPlan.getFilterConditions().isEmpty()) {
            QueryPlan.Builder builder = new QueryPlan.Builder(question)
                    .status(QueryPlanStatus.SUPPORTED)
                    .intent(priorPlan.getIntent());

            for (String tbl : priorPlan.getTargetTables()) {
                builder.addTargetTable(tbl);
            }
            for (SelectField sf : priorPlan.getSelectFields()) {
                builder.addSelectField(sf);
            }
            for (JoinCondition jc : priorPlan.getJoinConditions()) {
                builder.addJoinCondition(jc);
            }
            for (FilterCondition fc : priorPlan.getFilterConditions()) {
                builder.addFilterCondition(fc);
            }
            for (FilterCondition fc : deltaPlan.getFilterConditions()) {
                builder.addFilterCondition(fc);
            }
            return builder.build();
        }

        return null;
    }

    private String findReferencedColumn(String text, String table, SchemaMetadata schema) {
        if (!schema.hasTable(table)) return null;
        for (com.nl2sql.metadata.ColumnMetadata col : schema.getTable(table).getColumns()) {
            String colName = col.getName();
            String normalizedCol = colName.replace("_", " ").toLowerCase();
            if (text.contains(normalizedCol) || text.contains(colName.toLowerCase())) {
                return colName;
            }
        }
        return null;
    }

    public ConversationSession getSession() {
        return session;
    }
}
