package com.nl2sql.evaluation;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the aggregate metrics, pass rates, and detailed breakdown of a benchmark run.
 */
public class BenchmarkReport {

    public static class CaseResult {
        public final BenchmarkCase benchmarkCase;
        public final boolean passed;
        public final String actualStatus;
        public final int actualRows;
        public final String failureReason;
        public final long durationMs;

        public CaseResult(BenchmarkCase benchmarkCase, boolean passed, String actualStatus, int actualRows, String failureReason, long durationMs) {
            this.benchmarkCase = benchmarkCase;
            this.passed = passed;
            this.actualStatus = actualStatus;
            this.actualRows = actualRows;
            this.failureReason = failureReason;
            this.durationMs = durationMs;
        }
    }

    private final List<CaseResult> results = new ArrayList<>();

    public void addResult(CaseResult result) {
        results.add(result);
    }

    public int getTotalCount() {
        return results.size();
    }

    public int getPassedCount() {
        return (int) results.stream().filter(r -> r.passed).count();
    }

    public int getFailedCount() {
        return getTotalCount() - getPassedCount();
    }

    public double getAccuracyRate() {
        return results.isEmpty() ? 0.0 : ((double) getPassedCount() / getTotalCount()) * 100.0;
    }

    public List<CaseResult> getResults() {
        return results;
    }

    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=======================================================================\n");
        sb.append("                     EVALUATION BENCHMARK REPORT                      \n");
        sb.append("=======================================================================\n");
        sb.append(String.format("Total Test Cases:   %d\n", getTotalCount()));
        sb.append(String.format("Passed Cases:       %d\n", getPassedCount()));
        sb.append(String.format("Failed Cases:       %d\n", getFailedCount()));
        sb.append(String.format("Overall Accuracy:   %.1f%%\n", getAccuracyRate()));
        sb.append("-----------------------------------------------------------------------\n");
        sb.append("DETAILED BREAKDOWN BY CATEGORY:\n");

        for (CaseResult r : results) {
            String statusSymbol = r.passed ? "[PASS]" : "[FAIL]";
            sb.append(String.format("  %-6s %-4s | %-16s | \"%s\"\n",
                    statusSymbol,
                    r.benchmarkCase.getId(),
                    r.benchmarkCase.getCategory(),
                    r.benchmarkCase.getQuestion()));
            if (!r.passed) {
                sb.append(String.format("         Reason: %s\n", r.failureReason));
            }
        }
        sb.append("=======================================================================\n");
        return sb.toString();
    }
}
