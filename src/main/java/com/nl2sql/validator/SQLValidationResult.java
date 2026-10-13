package com.nl2sql.validator;

import java.util.Collections;
import java.util.Set;

/**
 * Result object representing the outcome of an independent SQL safety and schema validation check.
 */
public class SQLValidationResult {

    private final boolean valid;
    private final String reason;
    private final Set<String> validatedTables;
    private final Set<String> validatedColumns;

    public SQLValidationResult(boolean valid, String reason, Set<String> validatedTables, Set<String> validatedColumns) {
        this.valid = valid;
        this.reason = reason;
        this.validatedTables = validatedTables != null ? Collections.unmodifiableSet(validatedTables) : Collections.emptySet();
        this.validatedColumns = validatedColumns != null ? Collections.unmodifiableSet(validatedColumns) : Collections.emptySet();
    }

    public static SQLValidationResult success(Set<String> validatedTables, Set<String> validatedColumns) {
        return new SQLValidationResult(true, "Validation Passed: Query is safe and schema-compliant", validatedTables, validatedColumns);
    }

    public static SQLValidationResult failure(String reason) {
        return new SQLValidationResult(false, reason, Collections.emptySet(), Collections.emptySet());
    }

    public boolean isValid() {
        return valid;
    }

    public String getReason() {
        return reason;
    }

    public Set<String> getValidatedTables() {
        return validatedTables;
    }

    public Set<String> getValidatedColumns() {
        return validatedColumns;
    }

    public String getFormattedSummary() {
        StringBuilder sb = new StringBuilder();
        if (valid) {
            sb.append("VALIDATION STATUS: PASSED\n");
            sb.append("  Details: ").append(reason).append("\n");
            sb.append("  Validated Tables: ").append(validatedTables).append("\n");
            sb.append("  Validated Columns: ").append(validatedColumns).append("\n");
        } else {
            sb.append("VALIDATION STATUS: REJECTED\n");
            sb.append("  Reason: ").append(reason).append("\n");
        }
        return sb.toString();
    }
}
