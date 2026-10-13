package com.nl2sql.pipeline;

import com.nl2sql.client.MockLLMClient;
import com.nl2sql.database.CompanyDatabaseCreator;
import com.nl2sql.database.DatabaseContext;
import com.nl2sql.generator.GenericQueryPlanSQLGenerator;
import com.nl2sql.plan.LLMQueryPlanner;
import com.nl2sql.plan.QueryPlanStatus;
import com.nl2sql.plan.RuleBasedQueryPlanner;
import com.nl2sql.validator.DefaultSQLValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 18: Universal Pipeline End-to-End Orchestration Unit Tests.
 */
public class UniversalPipelineTest {

    private static final String COMPANY_DB_PATH = "data/company.db";

    @BeforeAll
    public static void setUp() throws Exception {
        CompanyDatabaseCreator.createCompanyDatabase(COMPANY_DB_PATH);
    }

    @Test
    @DisplayName("1. UniversalPipeline - Offline Rule-Based Query Execution")
    public void testOfflineExecution() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            UniversalPipeline pipeline = new UniversalPipeline(context, false);
            assertEquals("OFFLINE (Rule-Based)", pipeline.getModeName());

            PipelineResult result = pipeline.process("List all employees");
            assertTrue(result.isSuccess());
            assertNotNull(result.getPlan());
            assertEquals(QueryPlanStatus.SUPPORTED, result.getPlan().getStatus());
            assertNotNull(result.getGeneratedQuery());
            assertTrue(result.getGeneratedQuery().getSql().contains("FROM employees"));
            assertTrue(result.getValidationResult().isValid());
            assertEquals(4, result.getQueryResult().getRowCount());
            assertTrue(result.getAnswerResult().isSuccess());
        }
    }

    @Test
    @DisplayName("2. UniversalPipeline - Online Mock LLM Planner Execution")
    public void testOnlineMockExecution() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            String json = "{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"intent\": \"SELECT\",\n" +
                    "  \"targetTables\": [\"employees\"],\n" +
                    "  \"selectFields\": [{\"tableName\": \"employees\", \"columnName\": \"employee_name\"}],\n" +
                    "  \"limit\": 2\n" +
                    "}";
            MockLLMClient mockClient = new MockLLMClient(false, json);
            LLMQueryPlanner planner = new LLMQueryPlanner(mockClient);

            UniversalPipeline pipeline = new UniversalPipeline(
                    context,
                    planner,
                    new GenericQueryPlanSQLGenerator(),
                    new DefaultSQLValidator(),
                    new com.nl2sql.executor.DefaultDatabaseExecutor(context.getDatabaseManager()),
                    new com.nl2sql.answer.UniversalAnswerGenerator(),
                    "ONLINE (Mock LLM)"
            );

            PipelineResult result = pipeline.process("Show two employee names");
            assertTrue(result.isSuccess());
            assertEquals(2, result.getQueryResult().getRowCount());
            assertTrue(result.getFormattedSummary().contains("PIPELINE RESULT (ONLINE (Mock LLM)"));
        }
    }

    @Test
    @DisplayName("3. UniversalPipeline - Unsupported Query Refusal")
    public void testUnsupportedQueryRefusal() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            UniversalPipeline pipeline = new UniversalPipeline(context, false);
            PipelineResult result = pipeline.process("What is the recipe for chocolate cake?");

            assertFalse(result.isSuccess());
            assertEquals(QueryPlanStatus.UNSUPPORTED, result.getPlan().getStatus());
            assertNull(result.getQueryResult());
            assertFalse(result.getAnswerResult().isSuccess());
            assertTrue(result.getAnswerResult().getErrorMessage().contains("Unsupported Question"));
        }
    }

    @Test
    @DisplayName("4. UniversalPipeline - Ambiguous Query Refusal")
    public void testAmbiguousQueryRefusal() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            UniversalPipeline pipeline = new UniversalPipeline(context, false);
            PipelineResult result = pipeline.process("Show names");

            assertFalse(result.isSuccess());
            assertEquals(QueryPlanStatus.AMBIGUOUS, result.getPlan().getStatus());
            assertNull(result.getQueryResult());
            assertTrue(result.getAnswerResult().isClarificationRequired());
        }
    }
}
