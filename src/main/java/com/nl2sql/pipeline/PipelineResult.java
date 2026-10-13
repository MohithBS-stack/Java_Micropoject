package com.nl2sql.pipeline;

import com.nl2sql.answer.AnswerResult;
import com.nl2sql.executor.QueryResult;
import com.nl2sql.generator.GeneratedQuery;
import com.nl2sql.plan.QueryPlan;
import com.nl2sql.validator.SQLValidationResult;

/**
 * Encapsulates the complete result of executing a question through the UniversalPipeline.
 */
public class PipelineResult {

    private final String question;
    private final QueryPlan plan;
    private final GeneratedQuery generatedQuery;
    private final SQLValidationResult validationResult;
    private final QueryResult queryResult;
    private final AnswerResult answerResult;
    private final long executionTimeMs;
    private final String plannerMode;

    public PipelineResult(String question,
                          QueryPlan plan,
                          GeneratedQuery generatedQuery,
                          SQLValidationResult validationResult,
                          QueryResult queryResult,
                          AnswerResult answerResult,
                          long executionTimeMs,
                          String plannerMode) {
        this.question = question;
        this.plan = plan;
        this.generatedQuery = generatedQuery;
        this.validationResult = validationResult;
        this.queryResult = queryResult;
        this.answerResult = answerResult;
        this.executionTimeMs = executionTimeMs;
        this.plannerMode = plannerMode;
    }

    public String getQuestion() { return question; }
    public QueryPlan getPlan() { return plan; }
    public GeneratedQuery getGeneratedQuery() { return generatedQuery; }
    public SQLValidationResult getValidationResult() { return validationResult; }
    public QueryResult getQueryResult() { return queryResult; }
    public AnswerResult getAnswerResult() { return answerResult; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public String getPlannerMode() { return plannerMode; }

    public boolean isSuccess() {
        return queryResult != null && queryResult.isSuccess() && answerResult != null && answerResult.isSuccess();
    }

    public String getFormattedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("==================================================\n");
        sb.append("PIPELINE RESULT (").append(plannerMode).append(" - ").append(executionTimeMs).append("ms)\n");
        sb.append("==================================================\n");
        sb.append("QUESTION: ").append(question).append("\n\n");

        if (plan != null) {
            sb.append("--- INTERMEDIATE QUERY PLAN ---\n");
            sb.append(plan.toFormattedPlan()).append("\n\n");
        }

        if (generatedQuery != null && generatedQuery.isSuccess()) {
            sb.append("--- GENERATED SQL ---\n");
            sb.append(generatedQuery.getSql()).append("\n");
            sb.append("Parameters: ").append(generatedQuery.getParameters()).append("\n\n");
        }

        if (validationResult != null) {
            sb.append("--- VALIDATION ---\n");
            sb.append(validationResult.isValid() ? "PASSED (Safe SELECT)" : "FAILED: " + validationResult.getReason()).append("\n\n");
        }

        if (answerResult != null) {
            sb.append("--- ANSWER ---\n");
            sb.append(answerResult.getFormattedSummary());
        }

        return sb.toString();
    }
}
