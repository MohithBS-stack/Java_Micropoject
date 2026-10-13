package com.nl2sql.generator;

import com.nl2sql.nlp.Intent;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates a generated SQL query, its positional parameter bindings,
 * referenced schema tables, and execution safety status.
 */
public class GeneratedQuery {

    private final String sql;
    private final List<Object> parameters;
    private final List<String> referencedTables;
    private final Intent intent;
    private final boolean success;
    private final String errorMessage;

    public GeneratedQuery(String sql, List<Object> parameters, List<String> referencedTables, Intent intent) {
        this.sql = sql;
        this.parameters = Collections.unmodifiableList(parameters);
        this.referencedTables = Collections.unmodifiableList(referencedTables);
        this.intent = intent;
        this.success = true;
        this.errorMessage = null;
    }

    public GeneratedQuery(Intent intent, String errorMessage) {
        this.sql = null;
        this.parameters = Collections.emptyList();
        this.referencedTables = Collections.emptyList();
        this.intent = intent;
        this.success = false;
        this.errorMessage = errorMessage;
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getParameters() {
        return parameters;
    }

    public List<String> getReferencedTables() {
        return referencedTables;
    }

    public Intent getIntent() {
        return intent;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getFormattedSummary() {
        StringBuilder sb = new StringBuilder();
        if (!success) {
            sb.append("GENERATION FAILED / REJECTED:\n");
            sb.append("  Reason: ").append(errorMessage).append("\n");
        } else {
            sb.append("GENERATED SQL:\n");
            sb.append("  ").append(sql).append("\n");
            sb.append("PARAMETERS:\n");
            if (parameters.isEmpty()) {
                sb.append("  (None)\n");
            } else {
                for (int i = 0; i < parameters.size(); i++) {
                    sb.append("  [").append(i + 1).append("] ").append(parameters.get(i)).append("\n");
                }
            }
            sb.append("REFERENCED TABLES:\n  ").append(referencedTables).append("\n");
        }
        return sb.toString();
    }
}
