package com.nl2sql.domain.chemistry.validation;

/**
 * Represents a single validation warning or error discovered during dataset inspection.
 */
public class ValidationIssue {

    public enum Severity {
        WARNING,
        ERROR
    }

    private final Severity severity;
    private final String sheetName;
    private final Integer rowNumber; // nullable row number
    private final String columnName;
    private final String message;

    public ValidationIssue(Severity severity, String sheetName, Integer rowNumber, String columnName, String message) {
        this.severity = severity;
        this.sheetName = sheetName;
        this.rowNumber = rowNumber;
        this.columnName = columnName;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getSheetName() {
        return sheetName;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity).append("]");
        if (sheetName != null) {
            sb.append(" Sheet: ").append(sheetName);
        }
        if (rowNumber != null && rowNumber > 0) {
            sb.append(" (Row ").append(rowNumber).append(")");
        }
        if (columnName != null) {
            sb.append(" Col: ").append(columnName);
        }
        sb.append(" - ").append(message);
        return sb.toString();
    }
}
