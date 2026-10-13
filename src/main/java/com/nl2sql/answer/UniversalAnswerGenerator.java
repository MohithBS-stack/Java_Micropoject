package com.nl2sql.answer;

import com.nl2sql.client.LLMClient;
import com.nl2sql.executor.QueryResult;
import com.nl2sql.plan.AggregationType;
import com.nl2sql.plan.QueryIntent;
import com.nl2sql.plan.QueryPlan;
import com.nl2sql.plan.QueryPlanStatus;
import com.nl2sql.plan.SelectField;

import java.util.ArrayList;
import java.util.List;

/**
 * Universal answer generator transforming QueryPlan and QueryResult into natural,
 * human-readable conversational summaries and formatted answers.
 */
public class UniversalAnswerGenerator {

    private final RowFormatter rowFormatter;
    private final LLMClient llmClient;

    public UniversalAnswerGenerator() {
        this(new RowFormatter(), null);
    }

    public UniversalAnswerGenerator(RowFormatter rowFormatter) {
        this(rowFormatter, null);
    }

    public UniversalAnswerGenerator(LLMClient llmClient) {
        this(new RowFormatter(), llmClient);
    }

    public UniversalAnswerGenerator(RowFormatter rowFormatter, LLMClient llmClient) {
        this.rowFormatter = rowFormatter != null ? rowFormatter : new RowFormatter();
        this.llmClient = llmClient;
    }

    public AnswerResult generate(QueryPlan plan, QueryResult queryResult) {
        if (plan == null) {
            return AnswerResult.failure("No query plan provided.");
        }

        if (plan.getStatus() == QueryPlanStatus.UNSUPPORTED) {
            String reason = plan.getStatusReason() != null ? plan.getStatusReason() : "The question cannot be answered using the database schema.";
            return AnswerResult.failure("Unsupported Question: " + reason);
        }

        if (plan.getStatus() == QueryPlanStatus.AMBIGUOUS) {
            String reason = plan.getStatusReason() != null ? plan.getStatusReason() : "The question is ambiguous and has multiple interpretations.";
            return AnswerResult.clarification(reason);
        }

        if (queryResult == null || !queryResult.isSuccess()) {
            String errorMsg = (queryResult != null) ? queryResult.getErrorMessage() : "Execution produced null result";
            return AnswerResult.failure("Database execution failed: " + errorMsg);
        }

        if (queryResult.getRowCount() == 0 || queryResult.getRows().isEmpty()) {
            return AnswerResult.success("No matching records found in database.", 0);
        }

        // Try conversational LLM synthesis if available
        if (llmClient != null && queryResult.getRowCount() <= 10) {
            try {
                String llmSummary = synthesizeWithLLM(plan.getQuestion(), queryResult);
                if (llmSummary != null && !llmSummary.trim().isEmpty()) {
                    return AnswerResult.success(llmSummary.trim(), queryResult.getRowCount());
                }
            } catch (Exception ignored) {
                // Seamless fallback to deterministic synthesis
            }
        }

        // Deterministic Natural-Language Synthesis
        String conversationalSummary = synthesizeConversationalSummary(plan, queryResult);
        return AnswerResult.success(conversationalSummary, queryResult.getRowCount());
    }

    /**
     * Synthesizes conversational English sentences from query results deterministically.
     */
    public String synthesizeConversationalSummary(QueryPlan plan, QueryResult queryResult) {
        String primaryTable = (plan.getTargetTables() != null && !plan.getTargetTables().isEmpty())
                ? plan.getTargetTables().get(0)
                : "records";

        // 1. COUNT intent
        if (plan.getIntent() == QueryIntent.COUNT && queryResult.getRowCount() == 1) {
            Object countVal = queryResult.getRows().get(0).get(0);
            return "Found a total of " + countVal + " " + primaryTable + " records in the database.";
        }

        // 2. Pure Aggregations (AVG, SUM, MIN, MAX) with a single result row
        if (queryResult.getRowCount() == 1 && hasAggregations(plan)) {
            List<String> aggDescriptions = new ArrayList<>();
            List<String> cols = queryResult.getColumnNames();
            List<Object> row = queryResult.getRows().get(0);

            for (int i = 0; i < cols.size(); i++) {
                String col = cols.get(i);
                Object val = row.get(i);
                aggDescriptions.add(formatColumnName(col) + ": " + (val != null ? val : "N/A"));
            }

            return "Calculated summary for " + primaryTable + ": " + String.join(", ", aggDescriptions) + ".";
        }

        // 3. Single entity record
        if (queryResult.getRowCount() == 1) {
            List<String> cols = queryResult.getColumnNames();
            List<Object> row = queryResult.getRows().get(0);
            StringBuilder sb = new StringBuilder();
            sb.append("Found 1 record in ").append(primaryTable).append(":\n");
            for (int i = 0; i < cols.size(); i++) {
                sb.append("  • ").append(formatColumnName(cols.get(i))).append(": ")
                  .append(row.get(i) != null ? row.get(i) : "NULL").append("\n");
            }
            return sb.toString().trim();
        }

        // 4. Multi-row tabular presentation
        String formattedRows = rowFormatter.format(queryResult);
        return "Found " + queryResult.getRowCount() + " matching records in '" + primaryTable + "':\n\n" + formattedRows;
    }

    private boolean hasAggregations(QueryPlan plan) {
        if (plan.getSelectFields() == null) return false;
        for (SelectField sf : plan.getSelectFields()) {
            if (sf.getAggregationType() != null && sf.getAggregationType() != AggregationType.NONE) {
                return true;
            }
        }
        return false;
    }

    private String formatColumnName(String colName) {
        if (colName == null) return "";
        return colName.replace("_", " ").toLowerCase();
    }

    private String synthesizeWithLLM(String question, QueryResult queryResult) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful assistant. Synthesize a 1-2 sentence conversational English response ");
        sb.append("answering the user question using ONLY the provided query result data. Do not hallucinate.\n\n");
        sb.append("Question: ").append(question).append("\n");
        sb.append("Columns: ").append(queryResult.getColumnNames()).append("\n");
        sb.append("Data: ").append(queryResult.getRows()).append("\n\n");
        sb.append("Conversational Summary:");

        return llmClient.generateContent(sb.toString());
    }
}
