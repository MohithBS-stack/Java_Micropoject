package com.nl2sql.evaluation;

import com.nl2sql.plan.QueryPlanStatus;

/**
 * Represents a single test case in the Evaluation Benchmark Suite.
 */
public class BenchmarkCase {

    private final String id;
    private final String dbPath;
    private final String question;
    private final String category;
    private final QueryPlanStatus expectedStatus;
    private final Integer expectedMinRows;
    private final Integer expectedMaxRows;

    public BenchmarkCase(String id, String dbPath, String question, String category,
                         QueryPlanStatus expectedStatus, Integer expectedMinRows, Integer expectedMaxRows) {
        this.id = id;
        this.dbPath = dbPath;
        this.question = question;
        this.category = category;
        this.expectedStatus = expectedStatus;
        this.expectedMinRows = expectedMinRows;
        this.expectedMaxRows = expectedMaxRows;
    }

    public static BenchmarkCase success(String id, String dbPath, String question, String category, int minRows, int maxRows) {
        return new BenchmarkCase(id, dbPath, question, category, QueryPlanStatus.SUPPORTED, minRows, maxRows);
    }

    public static BenchmarkCase ambiguous(String id, String dbPath, String question, String category) {
        return new BenchmarkCase(id, dbPath, question, category, QueryPlanStatus.AMBIGUOUS, null, null);
    }

    public static BenchmarkCase unsupported(String id, String dbPath, String question, String category) {
        return new BenchmarkCase(id, dbPath, question, category, QueryPlanStatus.UNSUPPORTED, null, null);
    }

    public String getId() { return id; }
    public String getDbPath() { return dbPath; }
    public String getQuestion() { return question; }
    public String getCategory() { return category; }
    public QueryPlanStatus getExpectedStatus() { return expectedStatus; }
    public Integer getExpectedMinRows() { return expectedMinRows; }
    public Integer getExpectedMaxRows() { return expectedMaxRows; }
}
