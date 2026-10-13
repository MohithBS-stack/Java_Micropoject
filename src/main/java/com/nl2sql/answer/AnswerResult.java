package com.nl2sql.answer;

/**
 * Encapsulates the natural-language answer result generated from a QueryResult and ParsedQuestion.
 */
public class AnswerResult {

    private final boolean success;
    private final String answerText;
    private final String errorMessage;
    private final int rowCount;
    private final boolean clarificationRequired;

    public AnswerResult(boolean success, String answerText, String errorMessage, int rowCount, boolean clarificationRequired) {
        this.success = success;
        this.answerText = answerText;
        this.errorMessage = errorMessage;
        this.rowCount = rowCount;
        this.clarificationRequired = clarificationRequired;
    }

    public static AnswerResult success(String answerText, int rowCount) {
        return new AnswerResult(true, answerText, null, rowCount, false);
    }

    public static AnswerResult clarification(String clarificationReason) {
        return new AnswerResult(false, "Clarification Required: " + clarificationReason, clarificationReason, 0, true);
    }

    public static AnswerResult failure(String errorMessage) {
        return new AnswerResult(false, errorMessage, errorMessage, 0, false);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getAnswerText() {
        return answerText;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getRowCount() {
        return rowCount;
    }

    public boolean isClarificationRequired() {
        return clarificationRequired;
    }

    public String getFormattedSummary() {
        StringBuilder sb = new StringBuilder();
        if (success) {
            sb.append("ANSWER GENERATED:\n");
            sb.append("-------------------------------------------------\n");
            sb.append(answerText).append("\n");
            sb.append("-------------------------------------------------\n");
        } else if (clarificationRequired) {
            sb.append("ANSWER REJECTED (CLARIFICATION REQUIRED):\n");
            sb.append("  ").append(answerText).append("\n");
        } else {
            sb.append("ANSWER FAILED:\n");
            sb.append("  Error: ").append(errorMessage).append("\n");
        }
        return sb.toString();
    }
}
