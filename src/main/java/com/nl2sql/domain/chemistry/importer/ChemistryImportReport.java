package com.nl2sql.domain.chemistry.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds import statistics for each sheet and aggregates them.
 */
public class ChemistryImportReport {
    public static class SheetReport {
        public final String sheetName;
        public final String tableName;
        public int sourceRowCount;
        public int importedRowCount;
        public int skippedRowCount;
        public int failedRowCount;
        public final List<String> failureReasons = new ArrayList<>();
        public SheetReport(String sheetName, String tableName, int sourceRowCount) {
            this.sheetName = sheetName;
            this.tableName = tableName;
            this.sourceRowCount = sourceRowCount;
        }
    }

    private final List<SheetReport> sheetReports = new ArrayList<>();

    public void addSheetReport(SheetReport report) {
        sheetReports.add(report);
    }

    public List<SheetReport> getSheetReports() {
        return sheetReports;
    }

    public String getFormattedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Chemistry Import Report ===\n");
        for (SheetReport sr : sheetReports) {
            sb.append(String.format("Sheet '%s' -> Table '%s': sourceRows=%d, imported=%d, skipped=%d, failed=%d\n",
                    sr.sheetName, sr.tableName, sr.sourceRowCount, sr.importedRowCount, sr.skippedRowCount, sr.failedRowCount));
            if (!sr.failureReasons.isEmpty()) {
                sb.append("  Failure reasons:\n");
                for (String r : sr.failureReasons) {
                    sb.append("    - ").append(r).append('\n');
                }
            }
        }
        return sb.toString();
    }
}
