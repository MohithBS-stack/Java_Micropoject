package com.nl2sql.answer;

import com.nl2sql.executor.QueryResult;
import com.nl2sql.nlp.Intent;
import com.nl2sql.nlp.ParsedQuestion;

/**
 * Default implementation of AnswerGenerator.
 * Generates deterministic, anti-hallucinatory natural language answers strictly derived from QueryResult.
 */
public class DefaultAnswerGenerator implements AnswerGenerator {

    private final RowFormatter rowFormatter;

    public DefaultAnswerGenerator() {
        this.rowFormatter = new RowFormatter();
    }

    public DefaultAnswerGenerator(RowFormatter rowFormatter) {
        this.rowFormatter = rowFormatter;
    }

    @Override
    public AnswerResult generate(ParsedQuestion parsedQuestion, QueryResult queryResult) {
        // 1. Check if intent is UNKNOWN or unsupported
        if (parsedQuestion != null && parsedQuestion.getIntent() == Intent.UNKNOWN) {
            return AnswerResult.failure("I could not determine a supported database query for that question.");
        }

        // 2. Check if question required clarification
        if (parsedQuestion != null && parsedQuestion.isClarificationRequired()) {
            return AnswerResult.clarification(parsedQuestion.getClarificationReason());
        }

        // 3. Check for query execution failure
        if (queryResult == null || !queryResult.isSuccess()) {
            String errorMsg = (queryResult != null) ? queryResult.getErrorMessage() : "Null query result";
            return AnswerResult.failure("Unable to retrieve an answer because the database query failed: " + errorMsg);
        }

        // 4. Handle zero-row results
        if (queryResult.getRowCount() == 0 || queryResult.getRows().isEmpty()) {
            return AnswerResult.success("No matching records were found in the database.", 0);
        }

        // 5. Build intent-aware presentation header
        String header = getHeaderForIntent(parsedQuestion != null ? parsedQuestion.getIntent() : null);

        // 6. Format returned rows dynamically using RowFormatter
        String formattedData = rowFormatter.format(queryResult);
        String finalAnswerText = header + formattedData;

        return AnswerResult.success(finalAnswerText, queryResult.getRowCount());
    }

    private String getHeaderForIntent(Intent intent) {
        if (intent == null) {
            return "";
        }
        switch (intent) {
            case FIND_REAGENTS:
                return "Matching reagent records:\n";
            case FIND_OBSERVATION:
                return "Matching observation records:\n";
            case FIND_PROCEDURE:
                return "Matching procedure records:\n";
            case FIND_TEST:
                return "Matching test records:\n";
            case COUNT_RECORDS:
                return "Count result:\n";
            default:
                return "Query result:\n";
        }
    }
}
