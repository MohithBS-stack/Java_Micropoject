package com.nl2sql.executor;

import java.util.Collections;
import java.util.List;

/**
 * Generic data model capturing the outcome of a database query execution.
 * Independent of domain-specific logic.
 */
public class QueryResult {

    private final boolean success;
    private final String errorMessage;
    private final List<String> columnNames;
    private final List<List<Object>> rows;
    private final int rowCount;

    public QueryResult(boolean success, String errorMessage, List<String> columnNames, List<List<Object>> rows) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.columnNames = columnNames != null ? Collections.unmodifiableList(columnNames) : Collections.emptyList();
        this.rows = rows != null ? Collections.unmodifiableList(rows) : Collections.emptyList();
        this.rowCount = this.rows.size();
    }

    public static QueryResult success(List<String> columnNames, List<List<Object>> rows) {
        return new QueryResult(true, null, columnNames, rows);
    }

    public static QueryResult failure(String errorMessage) {
        return new QueryResult(false, errorMessage, Collections.emptyList(), Collections.emptyList());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public int getRowCount() {
        return rowCount;
    }

    public String getFormattedSummary() {
        StringBuilder sb = new StringBuilder();
        if (!success) {
            sb.append("EXECUTION FAILED / REJECTED:\n");
            sb.append("  Reason: ").append(errorMessage).append("\n");
        } else {
            sb.append("EXECUTION SUCCESSFUL:\n");
            sb.append("  Columns: ").append(columnNames).append("\n");
            sb.append("  Row Count: ").append(rowCount).append("\n");
            if (rows.isEmpty()) {
                sb.append("  Data: (No rows returned)\n");
            } else {
                sb.append("  Data:\n");
                for (int i = 0; i < rows.size(); i++) {
                    sb.append("    Row ").append(i + 1).append(": ").append(rows.get(i)).append("\n");
                }
            }
        }
        return sb.toString();
    }
}
