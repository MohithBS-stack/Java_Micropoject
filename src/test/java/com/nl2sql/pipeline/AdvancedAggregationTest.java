package com.nl2sql.pipeline;

import com.nl2sql.client.LLMClient;
import com.nl2sql.client.MockLLMClient;
import com.nl2sql.database.DatabaseContext;
import com.nl2sql.executor.DatabaseExecutor;
import com.nl2sql.executor.DefaultDatabaseExecutor;
import com.nl2sql.executor.QueryResult;
import com.nl2sql.generator.GeneratedQuery;
import com.nl2sql.generator.GenericQueryPlanSQLGenerator;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.plan.*;
import com.nl2sql.validator.DefaultSQLValidator;
import com.nl2sql.validator.SQLValidationResult;
import com.nl2sql.validator.SQLValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 19: Advanced Query Capabilities (GROUP BY, HAVING, AVG, SUM, MIN, MAX).
 */
public class AdvancedAggregationTest {

    private static final String COMPANY_DB_PATH = Paths.get("data", "company.db").toAbsolutePath().toString();

    private final GenericQueryPlanSQLGenerator sqlGenerator = new GenericQueryPlanSQLGenerator();
    private final SQLValidator validator = new DefaultSQLValidator();

    @Test
    @DisplayName("1. Direct QueryPlan with GROUP BY and AVG aggregation")
    public void testGroupByAndAvg() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();

            QueryPlan plan = new QueryPlan.Builder("Average salary per department")
                    .status(QueryPlanStatus.SUPPORTED)
                    .intent(QueryIntent.SELECT)
                    .addTargetTable("employees")
                    .addSelectField(new SelectField("employees", "department_id"))
                    .addSelectField(new SelectField("employees", "salary", "avg_salary", AggregationType.AVG))
                    .addGroupByField(new SelectField("employees", "department_id"))
                    .build();

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            assertTrue(query.isSuccess());
            assertTrue(query.getSql().contains("GROUP BY"));
            assertTrue(query.getSql().contains("AVG("));

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid(), "Validation error: " + valResult.getReason());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            assertTrue(result.isSuccess());
            assertTrue(result.getRows().size() >= 2);
        }
    }

    @Test
    @DisplayName("2. Direct QueryPlan with GROUP BY, COUNT, and HAVING condition")
    public void testGroupByAndHaving() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();

            QueryPlan plan = new QueryPlan.Builder("Departments with more than 1 employee")
                    .status(QueryPlanStatus.SUPPORTED)
                    .intent(QueryIntent.SELECT)
                    .addTargetTable("employees")
                    .addSelectField(new SelectField("employees", "department_id"))
                    .addSelectField(new SelectField("employees", "*", "emp_count", AggregationType.COUNT))
                    .addGroupByField(new SelectField("employees", "department_id"))
                    .addHavingCondition(new HavingCondition("employees", "*", AggregationType.COUNT, Operator.GREATER_THAN, 1))
                    .build();

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            assertTrue(query.isSuccess());
            assertTrue(query.getSql().contains("GROUP BY"));
            assertTrue(query.getSql().contains("HAVING COUNT(*) > ?"));
            assertEquals(1, query.getParameters().size());

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid(), "Validation error: " + valResult.getReason());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            assertTrue(result.isSuccess());
            // Dept 1 has 2 employees (Alice & Bob), Dept 2 has Charlie, Dept 3 has Diana
            assertEquals(1, result.getRows().size());
            assertEquals(1, ((Number) result.getRows().get(0).get(0)).intValue());
        }
    }

    @Test
    @DisplayName("3. Aggregations: SUM, MIN, MAX")
    public void testSumMinMax() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();

            QueryPlan plan = new QueryPlan.Builder("Min, max, and total salary")
                    .status(QueryPlanStatus.SUPPORTED)
                    .intent(QueryIntent.SELECT)
                    .addTargetTable("employees")
                    .addSelectField(new SelectField("employees", "salary", "min_sal", AggregationType.MIN))
                    .addSelectField(new SelectField("employees", "salary", "max_sal", AggregationType.MAX))
                    .addSelectField(new SelectField("employees", "salary", "tot_sal", AggregationType.SUM))
                    .build();

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            assertTrue(query.isSuccess());
            assertTrue(query.getSql().contains("MIN("));
            assertTrue(query.getSql().contains("MAX("));
            assertTrue(query.getSql().contains("SUM("));

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            assertTrue(result.isSuccess());
            assertEquals(1, result.getRows().size());
            double minSal = ((Number) result.getRows().get(0).get(0)).doubleValue();
            double maxSal = ((Number) result.getRows().get(0).get(1)).doubleValue();
            assertEquals(65000.0, minSal);
            assertEquals(110000.0, maxSal);
        }
    }

    @Test
    @DisplayName("4. LLM Query Plan Mapper parsing GROUP BY and HAVING")
    public void testLLMQueryPlanMapperGroupByHaving() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Which departments have more than 1 employee?";

            String jsonResponse = "{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"intent\": \"SELECT\",\n" +
                    "  \"targetTables\": [\"employees\"],\n" +
                    "  \"selectFields\": [\n" +
                    "    {\"tableName\": \"employees\", \"columnName\": \"department_id\"},\n" +
                    "    {\"tableName\": \"employees\", \"columnName\": \"*\", \"aggregationType\": \"COUNT\", \"alias\": \"cnt\"}\n" +
                    "  ],\n" +
                    "  \"groupBy\": [\"department_id\"],\n" +
                    "  \"having\": [\n" +
                    "    {\"tableName\": \"employees\", \"columnName\": \"*\", \"aggregationType\": \"COUNT\", \"operator\": \">\", \"value\": 1}\n" +
                    "  ]\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, jsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertEquals(1, plan.getGroupByFields().size());
            assertEquals("department_id", plan.getGroupByFields().get(0).getColumnName());
            assertEquals(1, plan.getHavingConditions().size());
            assertEquals(Operator.GREATER_THAN, plan.getHavingConditions().get(0).getOperator());

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            assertTrue(query.isSuccess());

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            assertTrue(result.isSuccess());
            assertEquals(1, result.getRows().size());
        }
    }

    @Test
    @DisplayName("5. Hallucination defense on nonexistent column in GROUP BY")
    public void testHallucinationDefenseGroupBy() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Group by imaginary column";

            String jsonResponse = "{\n" +
                    "  \"status\": \"SUPPORTED\",\n" +
                    "  \"intent\": \"SELECT\",\n" +
                    "  \"targetTables\": [\"employees\"],\n" +
                    "  \"selectFields\": [{\"tableName\": \"employees\", \"columnName\": \"employee_id\"}],\n" +
                    "  \"groupBy\": [\"imaginary_col\"]\n" +
                    "}";

            LLMClient mockClient = new MockLLMClient(false, jsonResponse);
            QuestionUnderstandingEngine planner = new LLMQueryPlanner(mockClient);

            QueryPlan plan = planner.understand(question, schema);
            assertEquals(QueryPlanStatus.UNSUPPORTED, plan.getStatus());
            assertTrue(plan.getStatusReason().contains("Hallucination Defense"));
        }
    }
}
