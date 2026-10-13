package com.nl2sql.evaluation;

import com.nl2sql.database.CompanyDatabaseCreator;
import com.nl2sql.database.DatabaseContext;
import com.nl2sql.domain.chemistry.importer.ChemistryImportRunner;
import com.nl2sql.pipeline.PipelineResult;
import com.nl2sql.pipeline.UniversalPipeline;
import com.nl2sql.plan.QueryPlanStatus;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates evaluation benchmark suite execution across databases.
 * Calculates accuracy rates, validates security, and verifies refusals.
 */
public class BenchmarkRunner {

    private static final String COMPANY_DB = Paths.get("data", "company.db").toAbsolutePath().toString();
    private static final String CHEMISTRY_DB = Paths.get("data", "chemistry.db").toAbsolutePath().toString();

    public static List<BenchmarkCase> getStandardSuite() {
        List<BenchmarkCase> suite = new ArrayList<>();

        // --- Company DB Tests ---
        suite.add(BenchmarkCase.success("C01", COMPANY_DB, "List all employees", "Basic SELECT", 4, 4));
        suite.add(BenchmarkCase.success("C02", COMPANY_DB, "Show employee names", "Column Projection", 4, 4));
        suite.add(BenchmarkCase.success("C03", COMPANY_DB, "Show employees with salary greater than 50000", "Numeric Filter", 4, 4));
        suite.add(BenchmarkCase.success("C04", COMPANY_DB, "How many employees are there?", "COUNT Aggregation", 1, 1));
        suite.add(BenchmarkCase.success("C05", COMPANY_DB, "Show employees in the Engineering department", "Relational JOIN", 1, 4));
        suite.add(BenchmarkCase.success("C06", COMPANY_DB, "Show employees with salary > 70000 and department_id = 1", "Multi-Filter", 1, 3));
        suite.add(BenchmarkCase.ambiguous("C07", COMPANY_DB, "Show names", "Ambiguity Handling"));
        suite.add(BenchmarkCase.unsupported("C08", COMPANY_DB, "Who won the 2024 World Cup?", "Out-of-Domain Refusal"));
        suite.add(BenchmarkCase.success("C09", COMPANY_DB, "Show employees where name is '; DROP TABLE employees; --", "Injection Defense", 0, 0));
        suite.add(BenchmarkCase.success("C10", COMPANY_DB, "Show average salary of employees", "AVG Aggregation", 1, 1));
        suite.add(BenchmarkCase.success("C11", COMPANY_DB, "Show departments", "Basic SELECT", 3, 3));
        suite.add(BenchmarkCase.success("C12", COMPANY_DB, "How many departments are there?", "COUNT Aggregation", 1, 1));

        // --- Chemistry DB Tests ---
        suite.add(BenchmarkCase.success("K01", CHEMISTRY_DB, "List all reagents", "Basic SELECT", 10, 50));
        suite.add(BenchmarkCase.success("K02", CHEMISTRY_DB, "How many tests are there?", "COUNT Aggregation", 1, 1));
        suite.add(BenchmarkCase.success("K03", CHEMISTRY_DB, "List all analytes", "Basic SELECT", 10, 30));
        suite.add(BenchmarkCase.success("K04", CHEMISTRY_DB, "List all salts", "Basic SELECT", 5, 20));
        suite.add(BenchmarkCase.success("K05", CHEMISTRY_DB, "List all reactions", "Basic SELECT", 5, 20));
        suite.add(BenchmarkCase.unsupported("K06", CHEMISTRY_DB, "What is the capital of Australia?", "Out-of-Domain Refusal"));
        suite.add(BenchmarkCase.ambiguous("K07", CHEMISTRY_DB, "Show notes", "Ambiguity Handling"));
        suite.add(BenchmarkCase.success("K08", CHEMISTRY_DB, "How many reagents are there?", "COUNT Aggregation", 1, 1));

        return suite;
    }

    public static BenchmarkReport run(boolean enableOnline) {
        ensureDatabasesExist();
        List<BenchmarkCase> testSuite = getStandardSuite();
        BenchmarkReport report = new BenchmarkReport();

        for (BenchmarkCase testCase : testSuite) {
            long start = System.currentTimeMillis();
            try (DatabaseContext context = DatabaseContext.open(testCase.getDbPath())) {
                UniversalPipeline pipeline = new UniversalPipeline(context, enableOnline);
                PipelineResult result = pipeline.process(testCase.getQuestion());
                long duration = System.currentTimeMillis() - start;

                boolean passed;
                String failureReason = null;
                int rowCount = (result.getQueryResult() != null) ? result.getQueryResult().getRowCount() : 0;
                String statusStr = (result.getPlan() != null) ? result.getPlan().getStatus().name() : "NULL";

                if (testCase.getExpectedStatus() == QueryPlanStatus.SUPPORTED) {
                    if (result.getPlan() == null || result.getPlan().getStatus() != QueryPlanStatus.SUPPORTED) {
                        passed = false;
                        failureReason = "Expected SUPPORTED plan but got: " + statusStr;
                    } else if (result.getQueryResult() == null || !result.getQueryResult().isSuccess()) {
                        passed = false;
                        failureReason = "Execution failed: " + (result.getQueryResult() != null ? result.getQueryResult().getErrorMessage() : "null");
                    } else if (rowCount < testCase.getExpectedMinRows() || rowCount > testCase.getExpectedMaxRows()) {
                        passed = false;
                        failureReason = String.format("Expected rows between %d and %d, got %d",
                                testCase.getExpectedMinRows(), testCase.getExpectedMaxRows(), rowCount);
                    } else {
                        passed = true;
                    }
                } else if (testCase.getExpectedStatus() == QueryPlanStatus.AMBIGUOUS) {
                    passed = (result.getPlan() != null && result.getPlan().getStatus() == QueryPlanStatus.AMBIGUOUS);
                    if (!passed) failureReason = "Expected AMBIGUOUS status but got: " + statusStr;
                } else if (testCase.getExpectedStatus() == QueryPlanStatus.UNSUPPORTED) {
                    passed = (result.getPlan() != null && result.getPlan().getStatus() == QueryPlanStatus.UNSUPPORTED);
                    if (!passed) failureReason = "Expected UNSUPPORTED status but got: " + statusStr;
                } else {
                    passed = false;
                    failureReason = "Unknown expected status: " + testCase.getExpectedStatus();
                }

                report.addResult(new BenchmarkReport.CaseResult(testCase, passed, statusStr, rowCount, failureReason, duration));

            } catch (Exception ex) {
                long duration = System.currentTimeMillis() - start;
                report.addResult(new BenchmarkReport.CaseResult(testCase, false, "ERROR", 0, ex.getMessage(), duration));
            }
        }

        return report;
    }

    private static void ensureDatabasesExist() {
        try {
            if (!Files.exists(Paths.get(COMPANY_DB))) {
                CompanyDatabaseCreator.createCompanyDatabase();
            }
            if (!Files.exists(Paths.get(CHEMISTRY_DB))) {
                ChemistryImportRunner.run();
            }
        } catch (Exception ignored) {}
    }
}
