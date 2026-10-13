package com.nl2sql.domain.chemistry.validation;

import java.util.*;

/**
 * Encapsulates the complete dataset validation report for salt_analysis_database.xlsx.
 */
public class ValidationReport {

    public enum Status {
        PASS,
        WARNING,
        ERROR
    }

    private final String workbookPath;
    private final Map<String, List<String>> sheetColumnsMap;
    private final Map<String, Integer> sheetRowCountsMap;
    private final List<ValidationIssue> issues;
    private final Status overallStatus;

    public ValidationReport(String workbookPath, Map<String, List<String>> sheetColumnsMap,
                            Map<String, Integer> sheetRowCountsMap, List<ValidationIssue> issues, Status overallStatus) {
        this.workbookPath = workbookPath;
        this.sheetColumnsMap = Collections.unmodifiableMap(sheetColumnsMap);
        this.sheetRowCountsMap = Collections.unmodifiableMap(sheetRowCountsMap);
        this.issues = Collections.unmodifiableList(issues);
        this.overallStatus = overallStatus;
    }

    public String getWorkbookPath() {
        return workbookPath;
    }

    public Map<String, List<String>> getSheetColumnsMap() {
        return sheetColumnsMap;
    }

    public Map<String, Integer> getSheetRowCountsMap() {
        return sheetRowCountsMap;
    }

    public List<ValidationIssue> getIssues() {
        return issues;
    }

    public Status getOverallStatus() {
        return overallStatus;
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.getSeverity() == ValidationIssue.Severity.ERROR);
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(i -> i.getSeverity() == ValidationIssue.Severity.WARNING);
    }

    public Set<String> getDetectedSheets() {
        return sheetRowCountsMap.keySet();
    }

    public String getFormattedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=================================================================\n");
        sb.append("         CHEMISTRY DATASET VALIDATION REPORT                     \n");
        sb.append("=================================================================\n");
        sb.append("Workbook Path: ").append(workbookPath).append("\n");
        sb.append("Overall Status: ").append(overallStatus).append("\n");
        sb.append("Total Sheets Detected: ").append(sheetRowCountsMap.size()).append("\n\n");

        sb.append("--- 1. SHEETS AND ROW COUNTS ---\n");
        for (Map.Entry<String, Integer> entry : sheetRowCountsMap.entrySet()) {
            sb.append(String.format("  - %-30s : %d data row(s)\n", entry.getKey(), entry.getValue()));
        }

        sb.append("\n--- 2. DETECTED COLUMNS PER SHEET ---\n");
        for (Map.Entry<String, List<String>> entry : sheetColumnsMap.entrySet()) {
            sb.append("  Sheet: ").append(entry.getKey()).append("\n");
            sb.append("    Columns: ").append(entry.getValue()).append("\n");
        }

        sb.append("\n--- 3. DATA INTEGRITY ISSUES & ISSUES SUMMARY ---\n");
        long errorCount = issues.stream().filter(i -> i.getSeverity() == ValidationIssue.Severity.ERROR).count();
        long warningCount = issues.stream().filter(i -> i.getSeverity() == ValidationIssue.Severity.WARNING).count();
        sb.append(String.format("Total Issues Found: %d (Errors: %d, Warnings: %d)\n", issues.size(), errorCount, warningCount));

        if (issues.isEmpty()) {
            sb.append("  (No errors or warnings detected!)\n");
        } else {
            for (ValidationIssue issue : issues) {
                sb.append("  ").append(issue.toString()).append("\n");
            }
        }

        sb.append("\n=================================================================\n");
        sb.append("SUMMARY STATUS: ").append(overallStatus).append("\n");
        sb.append("=================================================================\n");
        return sb.toString();
    }
}
