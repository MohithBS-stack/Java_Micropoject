package com.nl2sql.routing;

/**
 * Mode indicator specifying which QuestionParser engine processed the user's question.
 */
public enum ParserMode {
    OFFLINE_RULE_BASED("Guaranteed Offline Rule-Based Engine"),
    ONLINE_LLM("Online LLM Question Parsing Service");

    private final String description;

    ParserMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
