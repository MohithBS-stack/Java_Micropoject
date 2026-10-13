package com.nl2sql.pipeline;

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
import com.nl2sql.validator.DefaultSQLValidator;
import com.nl2sql.validator.SQLValidationResult;
import com.nl2sql.validator.SQLValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 16: Universal Natural Language Pipeline End-to-End Test Suite.
 * Validates complete flow: Question -> QueryPlan -> SQL Generator -> SQLValidator -> DatabaseExecutor -> QueryResult.
 */
public class UniversalPipelineEndToEndTest {

    private static final String COMPANY_DB_PATH = "data/company.db";
    private static final String CHEM_DB_PATH = "data/chemistry.db";

    private final QuestionUnderstandingEngine planner = new RuleBasedQueryPlanner();
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
    @DisplayName("1. Company DB - List all employees")
    public void testCompanyListAllEmployees() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "List all employees";

            QueryPlan plan = planner.understand(question, schema);
            printHeader("TEST 1: COMPANY DB - LIST ALL EMPLOYEES");
            System.out.println(plan.toFormattedPlan());

            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            System.out.println(query.getFormattedSummary());
            assertTrue(query.isSuccess());

            SQLValidationResult valResult = validator.validate(query, schema);
            System.out.println("VALIDATION:\n  " + valResult.getFormattedSummary() + "\n");
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            System.out.println("EXECUTION RESULT:\n" + result.getFormattedSummary() + "\n");

            assertTrue(result.isSuccess());
            assertEquals(4, result.getRows().size());
        }
    }

    @Test
    @DisplayName("2. Company DB - Show employee names")
    public void testCompanyShowEmployeeNames() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show employee names";

            QueryPlan plan = planner.understand(question, schema);
            printHeader("TEST 2: COMPANY DB - SHOW EMPLOYEE NAMES");

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            System.out.println(query.getFormattedSummary());
            assertTrue(query.isSuccess());

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            System.out.println("EXECUTION RESULT:\n" + result.getFormattedSummary() + "\n");

            assertTrue(result.isSuccess());
            assertEquals(4, result.getRows().size());
            assertEquals("employee_name", result.getColumnNames().get(0));
            assertEquals("Alice Smith", result.getRows().get(0).get(0));
        }
    }

    @Test
    @DisplayName("3. Company DB - Show employees with salary greater than 50000")
    public void testCompanySalaryFilter() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show employees with salary greater than 50000";

            QueryPlan plan = planner.understand(question, schema);
            printHeader("TEST 3: COMPANY DB - SALARY FILTER");

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            System.out.println(query.getFormattedSummary());
            assertTrue(query.isSuccess());
            assertTrue(query.getSql().contains("WHERE"));
            assertEquals(1, query.getParameters().size());
            assertEquals(50000.0, query.getParameters().get(0));

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            System.out.println("EXECUTION RESULT:\n" + result.getFormattedSummary() + "\n");

            assertTrue(result.isSuccess());
            assertTrue(result.getRows().size() > 0);
        }
    }

    @Test
    @DisplayName("4. Company DB - How many employees are there?")
    public void testCompanyCountEmployees() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "How many employees are there?";

            QueryPlan plan = planner.understand(question, schema);
            printHeader("TEST 4: COMPANY DB - COUNT EMPLOYEES");

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            System.out.println(query.getFormattedSummary());
            assertTrue(query.isSuccess());
            assertTrue(query.getSql().contains("COUNT(*)"));

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            System.out.println("EXECUTION RESULT:\n" + result.getFormattedSummary() + "\n");

            assertTrue(result.isSuccess());
            assertEquals(1, result.getRows().size());
            assertEquals(4L, ((Number) result.getRows().get(0).get(0)).longValue());
        }
    }

    @Test
    @DisplayName("5. Company DB - Show employees in the Engineering department")
    public void testCompanyJoinDepartment() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show employees in the Engineering department";

            QueryPlan plan = planner.understand(question, schema);
            printHeader("TEST 5: COMPANY DB - JOIN EMPLOYEES AND DEPARTMENTS");

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            System.out.println(query.getFormattedSummary());
            assertTrue(query.isSuccess());
            assertTrue(query.getSql().contains("JOIN"));

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            System.out.println("EXECUTION RESULT:\n" + result.getFormattedSummary() + "\n");

            assertTrue(result.isSuccess());
            assertEquals(2, result.getRows().size()); // Alice Smith and Bob Jones
        }
    }

    @Test
    @DisplayName("6. Chemistry DB - List all reagents")
    public void testChemistryListAllReagents() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(CHEM_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "List all reagents";

            QueryPlan plan = planner.understand(question, schema);
            printHeader("TEST 6: CHEMISTRY DB - LIST ALL REAGENTS");

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            System.out.println(query.getFormattedSummary());
            assertTrue(query.isSuccess());

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            System.out.println("EXECUTION RESULT:\n" + result.getFormattedSummary() + "\n");

            assertTrue(result.isSuccess());
            assertEquals(17, result.getRows().size());
        }
    }

    @Test
    @DisplayName("7. Chemistry DB - How many tests are there?")
    public void testChemistryCountTests() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(CHEM_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "How many tests are there?";

            QueryPlan plan = planner.understand(question, schema);
            printHeader("TEST 7: CHEMISTRY DB - COUNT TESTS");

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            System.out.println(query.getFormattedSummary());
            assertTrue(query.isSuccess());

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            System.out.println("EXECUTION RESULT:\n" + result.getFormattedSummary() + "\n");

            assertTrue(result.isSuccess());
            assertEquals(1, result.getRows().size());
            assertTrue(((Number) result.getRows().get(0).get(0)).longValue() > 0);
        }
    }

    @Test
    @DisplayName("8. Multiple Filters Test")
    public void testMultipleFilters() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            printHeader("TEST 8: MULTIPLE FILTERS");

            QueryPlan plan = new QueryPlan.Builder("Custom multiple filter query")
                    .status(QueryPlanStatus.SUPPORTED)
                    .intent(QueryIntent.SELECT)
                    .addTargetTable("employees")
                    .addSelectField(new SelectField("employees", "employee_name"))
                    .addFilterCondition(new FilterCondition("employees", "salary", Operator.GREATER_THAN, 70000.0))
                    .addFilterCondition(new FilterCondition("employees", "department_id", Operator.EQUALS, 1))
                    .build();

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            System.out.println(query.getFormattedSummary());
            assertTrue(query.isSuccess());
            assertTrue(query.getSql().contains("AND"));
            assertEquals(2, query.getParameters().size());

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            System.out.println("EXECUTION RESULT:\n" + result.getFormattedSummary() + "\n");
            assertTrue(result.isSuccess());
            assertEquals(2, result.getRows().size()); // Alice Smith (85k) and Bob Jones (92k)
        }
    }

    @Test
    @DisplayName("9. ORDER BY Test")
    public void testOrderBy() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            printHeader("TEST 9: ORDER BY");

            QueryPlan plan = new QueryPlan.Builder("Order employees by salary desc")
                    .status(QueryPlanStatus.SUPPORTED)
                    .intent(QueryIntent.SELECT)
                    .addTargetTable("employees")
                    .addSelectField(new SelectField("employees", "employee_name"))
                    .addSelectField(new SelectField("employees", "salary"))
                    .addOrderSpecification(new OrderSpecification("employees", "salary", false))
                    .build();

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            System.out.println(query.getFormattedSummary());
            assertTrue(query.isSuccess());
            assertTrue(query.getSql().contains("ORDER BY"));

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            System.out.println("EXECUTION RESULT:\n" + result.getFormattedSummary() + "\n");
            assertTrue(result.isSuccess());
            assertEquals("Diana Prince", result.getRows().get(0).get(0)); // Highest salary 110k
        }
    }

    @Test
    @DisplayName("10. LIMIT Test")
    public void testLimit() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            printHeader("TEST 10: LIMIT");

            QueryPlan plan = new QueryPlan.Builder("Limit employees")
                    .status(QueryPlanStatus.SUPPORTED)
                    .intent(QueryIntent.SELECT)
                    .addTargetTable("employees")
                    .addSelectField(new SelectField("employees", "*"))
                    .limit(2)
                    .build();

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            System.out.println(query.getFormattedSummary());
            assertTrue(query.isSuccess());
            assertTrue(query.getSql().contains("LIMIT ?"));

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            System.out.println("EXECUTION RESULT:\n" + result.getFormattedSummary() + "\n");
            assertTrue(result.isSuccess());
            assertEquals(2, result.getRows().size());
        }
    }

    @Test
    @DisplayName("11. Ambiguous QueryPlan Refusal")
    public void testAmbiguousQueryPlanRefusal() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            printHeader("TEST 11: AMBIGUOUS QUERYPLAN REFUSAL");

            QueryPlan plan = planner.understand("Show names", schema);
            assertEquals(QueryPlanStatus.AMBIGUOUS, plan.getStatus());

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            assertFalse(query.isSuccess());
            assertTrue(query.getErrorMessage().contains("AMBIGUOUS"));

            SQLValidationResult valResult = validator.validate(query, schema);
            assertFalse(valResult.isValid());
        }
    }

    @Test
    @DisplayName("12. Unsupported QueryPlan Refusal")
    public void testUnsupportedQueryPlanRefusal() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            printHeader("TEST 12: UNSUPPORTED QUERYPLAN REFUSAL");

            QueryPlan plan = planner.understand("Who won the 2024 World Cup?", schema);
            assertEquals(QueryPlanStatus.UNSUPPORTED, plan.getStatus());

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            assertFalse(query.isSuccess());
            assertTrue(query.getErrorMessage().contains("UNSUPPORTED"));

            SQLValidationResult valResult = validator.validate(query, schema);
            assertFalse(valResult.isValid());
        }
    }

    @Test
    @DisplayName("13. Invalid Table / Column Schema Validation Failure")
    public void testInvalidTableColumnInQueryPlan() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            printHeader("TEST 13: INVALID TABLE / COLUMN DISCOVERY FAILURE");

            QueryPlan invalidPlan = new QueryPlan.Builder("Invalid query plan")
                    .status(QueryPlanStatus.SUPPORTED)
                    .intent(QueryIntent.SELECT)
                    .addTargetTable("employees")
                    .addSelectField(new SelectField("employees", "non_existent_column"))
                    .build();

            GeneratedQuery query = sqlGenerator.generate(invalidPlan, schema);
            assertFalse(query.isSuccess());
            assertTrue(query.getErrorMessage().contains("Schema Validation Error"));
        }
    }

    @Test
    @DisplayName("14. SQL Injection Parameter Defense Test")
    public void testSqlInjectionParameterDefense() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            printHeader("TEST 14: SQL INJECTION PARAMETER DEFENSE");

            String maliciousInput = "Engineering' OR '1'='1";

            QueryPlan plan = new QueryPlan.Builder("Malicious input test")
                    .status(QueryPlanStatus.SUPPORTED)
                    .intent(QueryIntent.SELECT)
                    .addTargetTable("departments")
                    .addSelectField(new SelectField("departments", "*"))
                    .addFilterCondition(new FilterCondition("departments", "department_name", Operator.EQUALS, maliciousInput))
                    .build();

            GeneratedQuery query = sqlGenerator.generate(plan, schema);
            System.out.println(query.getFormattedSummary());
            assertTrue(query.isSuccess());
            assertFalse(query.getSql().contains(maliciousInput));
            assertTrue(query.getSql().contains("WHERE d.department_name = ?"));

            SQLValidationResult valResult = validator.validate(query, schema);
            assertTrue(valResult.isValid());

            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            QueryResult result = executor.execute(query, valResult);
            System.out.println("EXECUTION RESULT:\n" + result.getFormattedSummary() + "\n");
            assertTrue(result.isSuccess());
            assertEquals(0, result.getRows().size()); // Safely parameterized! Injection neutralized!
        }
    }
}
