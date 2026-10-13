package com.nl2sql.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 22: Evaluation Benchmark Suite & Accuracy Scoring.
 */
public class EvaluationBenchmarkTest {

    @Test
    @DisplayName("Run standard evaluation benchmark suite and assert high accuracy rate")
    public void testBenchmarkExecution() {
        BenchmarkReport report = BenchmarkRunner.run(false);
        System.out.println(report.generateSummary());

        assertTrue(report.getTotalCount() >= 15);
        assertTrue(report.getAccuracyRate() >= 80.0,
                "Accuracy rate must be at least 80%, was: " + report.getAccuracyRate() + "%");
    }
}
