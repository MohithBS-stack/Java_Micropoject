package com.nl2sql.pipeline;

import com.nl2sql.client.GeminiLLMClient;
import com.nl2sql.client.LLMClient;
import com.nl2sql.client.MockLLMClient;
import com.nl2sql.database.CompanyDatabaseCreator;
import com.nl2sql.database.DatabaseContext;
import com.nl2sql.executor.DatabaseExecutor;
import com.nl2sql.executor.DefaultDatabaseExecutor;
import com.nl2sql.executor.QueryResult;
import com.nl2sql.generator.GenericQueryPlanSQLGenerator;
import com.nl2sql.generator.GeneratedQuery;
import com.nl2sql.generator.QueryPlanSQLGenerator;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.plan.*;
import com.nl2sql.routing.ConnectivityChecker;
import com.nl2sql.routing.LLMConfiguration;
import com.nl2sql.validator.DefaultSQLValidator;
import com.nl2sql.validator.SQLValidationResult;
import com.nl2sql.validator.SQLValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 17: LLM-Based Universal Query Understanding Test Suite.
 * Validates complete online + offline pipeline:
 * 1. Gemini JSON parsing (clean JSON, markdown codeblock, wrapped response)
 * 2. Schema-aware validation
 * 3. Hallucinated table detection
 * 4. Hallucinated column detection
 * 5. Invalid joins (invalid relationships, nonexistent table/column)
 * 6. Invalid operators
 * 7. Malformed JSON handling & fallback
 * 8. API timeout handling & fallback
 * 9. API failure handling & fallback
 * 10. Missing API key handling & fallback
 * 11. No Internet handling & fallback
 * 12. Gemini returning unsupported question
 * 13. Gemini returning ambiguous question
 * 14. Injection-like natural language & prompt injection defense
 * 15. Offline fallback end-to-end
 */
public class UniversalLLMIntegrationTest {

    private static final String COMPANY_DB_PATH = "data/company.db";
    private static final String CHEM_DB_PATH = "data/chemistry.db";

    private final QueryPlanSQLGenerator sqlGenerator = new GenericQueryPlanSQLGenerator();
    private final SQLValidator validator = new DefaultSQLValidator();

    @BeforeAll
    public static void setUp() throws Exception {
        CompanyDatabaseCreator.createCompanyDatabase(COMPANY_DB_PATH);
    }

    private void printHeader(String title) {
        System.out.println("==================================================");
        System.out.println(title);
        System.out.println("==================================================");
    }

    @Test
    @DisplayName("1. Gemini JSON Parsing - Standard JSON with filters, joins, orderBy, limit")
    public void testGeminiJsonParsingStandardStructure() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show engineering employees ordered by salary";

            String jsonResponse = "{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"intent\": \"SELECT\",\n" +
                    "  \"targetTables\": [\"employees\", \"departments\"],\n" +
                    "  \"selectFields\": [{\"tableName\": \"employees\", \"columnName\": \"employee_name\"}, {\"tableName\": \"employees\", \"columnName\": \"salary\"}],\n" +
                    "  \"filters\": [{\"tableName\": \"departments\", \"columnName\": \"department_name\", \"operator\": \"=\", \"value\": \"Engineering\"}],\n" +
                    "  \"joins\": [{\"leftTable\": \"employees\", \"leftColumn\": \"department_id\", \"rightTable\": \"departments\", \"rightColumn\": \"department_id\", \"type\": \"INNER\"}],\n" +
                    "  \"orderBy\": [{\"tableName\": \"employees\", \"columnName\": \"salary\", \"ascending\": false}],\n" +
                    "  \"limit\": 10\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, jsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            printHeader("TEST 1: GEMINI JSON PARSING - STANDARD STRUCTURE");
            System.out.println(plan.toFormattedPlan());

            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertEquals(2, plan.getSelectFields().size());
            assertEquals(1, plan.getFilterConditions().size());
            assertEquals(1, plan.getJoinConditions().size());
            assertEquals(1, plan.getOrderSpecifications().size());
            assertEquals(10, plan.getLimit());

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            assertTrue(result.isSuccess());
            assertEquals(2, result.getRows().size());
        }
    }

    @Test
    @DisplayName("2. Gemini JSON Parsing - Markdown codeblock format ```json ... ```")
    public void testGeminiJsonParsingMarkdownCodeblock() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(CHEM_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "List all reagents";

            String markdownResponse = "Here is the query plan:\n```json\n{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"intent\": \"SELECT\",\n" +
                    "  \"targetTables\": [\"reagents\"],\n" +
                    "  \"selectFields\": [{\"tableName\": \"reagents\", \"columnName\": \"*\"}]\n" +
                    "}\n```\nHope this helps!";

            LLMClient mockClient = new MockLLMClient(false, markdownResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            assertTrue(result.isSuccess());
            assertEquals(17, result.getRows().size());
        }
    }

    @Test
    @DisplayName("3. Schema-Aware Validation - Inferred target tables from select & filters")
    public void testSchemaAwareValidationInferredTargetTables() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "List employee names";

            // targetTables is omitted, parser should infer from selectFields
            String jsonResponse = "{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"intent\": \"SELECT\",\n" +
                    "  \"selectFields\": [{\"tableName\": \"employees\", \"columnName\": \"employee_name\"}]\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, jsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertTrue(plan.getTargetTables().contains("employees"));
        }
    }

    @Test
    @DisplayName("4. Hallucinated Table Detection - Nonexistent table 'passports'")
    public void testHallucinatedTableDetection() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show employee passports";

            String jsonResponse = "{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"intent\": \"SELECT\",\n" +
                    "  \"targetTables\": [\"passports\"],\n" +
                    "  \"selectFields\": [{\"tableName\": \"passports\", \"columnName\": \"passport_id\"}]\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, jsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.UNSUPPORTED, plan.getStatus());
            assertTrue(plan.getStatusReason().contains("Hallucination Defense"));
            assertTrue(plan.getStatusReason().contains("passports"));

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            assertFalse(query.isSuccess());
        }
    }

    @Test
    @DisplayName("5. Hallucinated Column Detection - Nonexistent column 'passport_number'")
    public void testHallucinatedColumnDetection() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show employee passport numbers";

            String jsonResponse = "{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"intent\": \"SELECT\",\n" +
                    "  \"targetTables\": [\"employees\"],\n" +
                    "  \"selectFields\": [{\"tableName\": \"employees\", \"columnName\": \"passport_number\"}]\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, jsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.UNSUPPORTED, plan.getStatus());
            assertTrue(plan.getStatusReason().contains("Hallucination Defense"));
            assertTrue(plan.getStatusReason().contains("passport_number"));

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            assertFalse(query.isSuccess());
        }
    }

    @Test
    @DisplayName("6. Invalid Join Detection - Nonexistent join column")
    public void testInvalidJoinNonexistentColumn() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show employees and departments";

            String jsonResponse = "{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"intent\": \"SELECT\",\n" +
                    "  \"targetTables\": [\"employees\", \"departments\"],\n" +
                    "  \"joins\": [{\"leftTable\": \"employees\", \"leftColumn\": \"nonexistent_id\", \"rightTable\": \"departments\", \"rightColumn\": \"department_id\"}]\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, jsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.UNSUPPORTED, plan.getStatus());
            assertTrue(plan.getStatusReason().contains("nonexistent_id") || plan.getStatusReason().contains("Hallucination Defense"));
        }
    }

    @Test
    @DisplayName("7. Invalid Join Detection - Unrelated tables with no FK relationship")
    public void testInvalidJoinUnrelatedTables() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show employees joined with projects on salary equals budget";

            // Joining employees.salary with departments.department_name (invalid relationship)
            String jsonResponse = "{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"intent\": \"SELECT\",\n" +
                    "  \"targetTables\": [\"employees\", \"departments\"],\n" +
                    "  \"joins\": [{\"leftTable\": \"employees\", \"leftColumn\": \"salary\", \"rightTable\": \"departments\", \"rightColumn\": \"department_name\"}]\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, jsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.UNSUPPORTED, plan.getStatus());
            assertTrue(plan.getStatusReason().contains("Validation Error") || plan.getStatusReason().contains("Invalid join"));
        }
    }

    @Test
    @DisplayName("8. Invalid Operator Detection - Disallowed operator 'DROP' or 'INVALID_OP'")
    public void testInvalidOperatorDetection() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show employees with weird operator";

            String jsonResponse = "{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"intent\": \"SELECT\",\n" +
                    "  \"targetTables\": [\"employees\"],\n" +
                    "  \"filters\": [{\"tableName\": \"employees\", \"columnName\": \"salary\", \"operator\": \"DROP\", \"value\": 50000}]\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, jsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.UNSUPPORTED, plan.getStatus());
            assertTrue(plan.getStatusReason().contains("Invalid or unsupported operator") || plan.getStatusReason().contains("Validation Error"));
        }
    }

    @Test
    @DisplayName("9. Malformed JSON - Fallback to Rule-Based Planner")
    public void testMalformedJsonTriggersOfflineFallback() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "List all employees";

            LLMClient malformedClient = new MockLLMClient(false, "{ \"status\": \"SUPPORTED\", UNPARSEABLE GARBAGE TEXT");
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(malformedClient);

            QueryPlan plan = planner.understand(question, schema);
            // Must fallback seamlessly to RuleBasedQueryPlanner
            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertTrue(plan.getTargetTables().contains("employees"));

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());
        }
    }

    @Test
    @DisplayName("10. API Timeout - Fallback to Rule-Based Planner")
    public void testApiTimeoutTriggersOfflineFallback() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "List all employees";

            // Mock throwing network/timeout exception
            LLMClient timeoutClient = new MockLLMClient(true, null);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(timeoutClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertTrue(plan.getTargetTables().contains("employees"));
        }
    }

    @Test
    @DisplayName("11. API Failure (HTTP Error) - Fallback to Rule-Based Planner")
    public void testApiFailureTriggersOfflineFallback() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show engineering department";

            LLMClient failureClient = new MockLLMClient(true, null);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(failureClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertTrue(plan.getTargetTables().contains("departments"));
        }
    }

    @Test
    @DisplayName("12. Missing API Key - Fallback to Rule-Based Planner")
    public void testMissingApiKeyTriggersOfflineFallback() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "List all employees";

            // Unconfigured LLM configuration
            LLMConfiguration unconfigured = new LLMConfiguration("", "gemini-1.5-flash");
            LLMClient client = new GeminiLLMClient(unconfigured);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(client, () -> true, new RuleBasedQueryPlanner());

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertTrue(plan.getTargetTables().contains("employees"));
        }
    }

    @Test
    @DisplayName("13. No Internet - Immediate Offline Fallback without Calling API")
    public void testNoInternetImmediateOfflineFallback() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "List all employees";

            MockLLMClient client = new MockLLMClient(false, "{}");
            ConnectivityChecker offlineChecker = () -> false;

            QuestionUnderstandingEngine planner = new LLMQueryPlanner(client, offlineChecker, new RuleBasedQueryPlanner());

            QueryPlan plan = planner.understand(question, schema);
            // LLM should not even be called when offline
            assertFalse(client.wasCalled());
            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertTrue(plan.getTargetTables().contains("employees"));
        }
    }

    @Test
    @DisplayName("14. Gemini Returning Unsupported Question")
    public void testGeminiReturningUnsupportedQuestion() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Who won the 2024 World Cup?";

            String jsonResponse = "{\n" +
                    "  \"status\": \"UNSUPPORTED\",\n" +
                    "  \"statusReason\": \"Question topic 'World Cup' is not covered by database schema.\"\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, jsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.UNSUPPORTED, plan.getStatus());
            assertEquals("Question topic 'World Cup' is not covered by database schema.", plan.getStatusReason());

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            assertFalse(query.isSuccess());
        }
    }

    @Test
    @DisplayName("15. Gemini Returning Ambiguous Question")
    public void testGeminiReturningAmbiguousQuestion() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show names";

            String jsonResponse = "{\n" +
                    "  \"status\": \"AMBIGUOUS\",\n" +
                    "  \"statusReason\": \"Ambiguous column 'names' matches multiple candidate tables: employees, departments\"\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, jsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.AMBIGUOUS, plan.getStatus());
            assertTrue(plan.getStatusReason().contains("Ambiguous"));

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            assertFalse(query.isSuccess());
        }
    }

    @Test
    @DisplayName("16. Raw SQL Rejection - Security Defense against LLM generating raw SQL")
    public void testRawSqlRejection() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Delete all employees";

            String maliciousJsonResponse = "{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"sql\": \"DROP TABLE employees\"\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, maliciousJsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.UNSUPPORTED, plan.getStatus());
            assertTrue(plan.getStatusReason().contains("Security Rejection"));

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            assertFalse(query.isSuccess());
        }
    }

    @Test
    @DisplayName("17. Injection-Like Natural Language - Injection Protection and Parameterized Execution")
    public void testInjectionLikeNaturalLanguageProtection() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String maliciousQuestion = "Show employees where name is '; DROP TABLE employees; --";

            // Even if LLM structures it into a filter with malicious value:
            String jsonResponse = "{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"intent\": \"SELECT\",\n" +
                    "  \"targetTables\": [\"employees\"],\n" +
                    "  \"selectFields\": [{\"tableName\": \"employees\", \"columnName\": \"*\"}],\n" +
                    "  \"filters\": [{\"tableName\": \"employees\", \"columnName\": \"employee_name\", \"operator\": \"=\", \"value\": \"'; DROP TABLE employees; --\"}]\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, jsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(maliciousQuestion, schema);
            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            assertTrue(query.isSuccess());
            // Must be parameterized (placeholder '?', not raw text injection)
            assertTrue(query.getSql().contains("?"));
            assertFalse(query.getSql().contains("DROP TABLE"));

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            // Safely executed with 0 matching rows and without dropping the table
            assertTrue(result.isSuccess());
            assertEquals(0, result.getRows().size());

            // Verify table still exists and is untouched
            assertTrue(schema.hasTable("employees"));
        }
    }

    @Test
    @DisplayName("18. Offline Fallback End-to-End Execution")
    public void testOfflineFallbackEndToEndExecution() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "List all employees";

            // Offline network checker
            ConnectivityChecker offline = () -> false;
            LLMClient dummyClient = new MockLLMClient(true, null);

            QuestionUnderstandingEngine planner = new LLMQueryPlanner(dummyClient, offline, new RuleBasedQueryPlanner());

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            assertTrue(result.isSuccess());
            assertEquals(4, result.getRows().size());
        }
    }

    @Test
    @DisplayName("19. Live Gemini API Test (Executed only when GEMINI_API_KEY is present)")
    public void testLiveGeminiIfConfigured() throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println("Live Gemini test skipped: GEMINI_API_KEY not configured.");
            return;
        }

        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "List all employees";

            LLMConfiguration config = new LLMConfiguration(apiKey, "gemini-1.5-flash");
            LLMClient liveClient = new GeminiLLMClient(config);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(liveClient, new com.nl2sql.routing.DefaultConnectivityChecker(), new RuleBasedQueryPlanner());

            QueryPlan plan = planner.understand(question, schema);
            printHeader("TEST 19: LIVE GEMINI API TEST");
            System.out.println(plan.toFormattedPlan());

            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            System.out.println("LIVE EXECUTION RESULT:\n" + result.getFormattedSummary());
            assertTrue(result.isSuccess());
        }
    }
}
