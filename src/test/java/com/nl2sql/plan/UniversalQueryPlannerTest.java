package com.nl2sql.plan;

import com.nl2sql.database.CompanyDatabaseCreator;
import com.nl2sql.database.DatabaseContext;
import com.nl2sql.metadata.SchemaMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 15: Universal Natural-Language Query Plan Test Suite.
 * Validates domain-independent QueryPlan resolution across company.db and chemistry.db.
 */
public class UniversalQueryPlannerTest {

    private static final String COMPANY_DB_PATH = "data/company.db";
    private static final String CHEM_DB_PATH = "data/chemistry.db";

    private final QuestionUnderstandingEngine planner = new RuleBasedQueryPlanner();

    @BeforeAll
    public static void setUp() throws Exception {
        CompanyDatabaseCreator.createCompanyDatabase(COMPANY_DB_PATH);
    }

    private void printPlanHeader(String testTitle, QueryPlan plan) {
        System.out.println("==================================================");
        System.out.println(testTitle);
        System.out.println("==================================================");
        System.out.println(plan.toFormattedPlan());
    }

    @Test
    @DisplayName("1. Company DB - Simple SELECT: List all employees")
    public void testCompanyListAllEmployees() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "List all employees";
            QueryPlan plan = planner.understand(question, schema);

            printPlanHeader("TEST 1: COMPANY DB - LIST ALL EMPLOYEES", plan);

            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertEquals(QueryIntent.SELECT, plan.getIntent());
            assertTrue(plan.getTargetTables().contains("employees"));
            assertEquals(1, plan.getSelectFields().size());
            assertEquals("*", plan.getSelectFields().get(0).getColumnName());
            assertTrue(plan.getFilterConditions().isEmpty());
            assertTrue(plan.getJoinConditions().isEmpty());
        }
    }

    @Test
    @DisplayName("2. Company DB - Column Resolution: Show employee names")
    public void testCompanyShowEmployeeNames() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show employee names";
            QueryPlan plan = planner.understand(question, schema);

            printPlanHeader("TEST 2: COMPANY DB - SHOW EMPLOYEE NAMES", plan);

            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertEquals(QueryIntent.SELECT, plan.getIntent());
            assertTrue(plan.getTargetTables().contains("employees"));
            assertEquals(1, plan.getSelectFields().size());
            assertEquals("employee_name", plan.getSelectFields().get(0).getColumnName());
        }
    }

    @Test
    @DisplayName("3. Company DB - Numeric Filter: Show employees with salary greater than 50000")
    public void testCompanySalaryFilter() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show employees with salary greater than 50000";
            QueryPlan plan = planner.understand(question, schema);

            printPlanHeader("TEST 3: COMPANY DB - SALARY FILTER", plan);

            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertEquals(QueryIntent.SELECT, plan.getIntent());
            assertTrue(plan.getTargetTables().contains("employees"));
            assertEquals(1, plan.getFilterConditions().size());

            FilterCondition fc = plan.getFilterConditions().get(0);
            assertEquals("employees", fc.getTableName());
            assertEquals("salary", fc.getColumnName());
            assertEquals(Operator.GREATER_THAN, fc.getOperator());
            assertEquals(50000.0, fc.getValue());
        }
    }

    @Test
    @DisplayName("4. Company DB - Aggregation: How many employees are there?")
    public void testCompanyCountEmployees() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "How many employees are there?";
            QueryPlan plan = planner.understand(question, schema);

            printPlanHeader("TEST 4: COMPANY DB - COUNT EMPLOYEES", plan);

            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertEquals(QueryIntent.COUNT, plan.getIntent());
            assertTrue(plan.getTargetTables().contains("employees"));
            assertEquals(1, plan.getSelectFields().size());
            assertEquals(AggregationType.COUNT, plan.getSelectFields().get(0).getAggregationType());
        }
    }

    @Test
    @DisplayName("5. Company DB - Join Resolution: Show employees in the Engineering department")
    public void testCompanyJoinDepartment() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show employees in the Engineering department";
            QueryPlan plan = planner.understand(question, schema);

            printPlanHeader("TEST 5: COMPANY DB - JOIN EMPLOYEES AND DEPARTMENTS", plan);

            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertEquals(QueryIntent.SELECT, plan.getIntent());
            assertTrue(plan.getTargetTables().contains("employees"));
            assertTrue(plan.getTargetTables().contains("departments"));

            assertEquals(1, plan.getJoinConditions().size());
            JoinCondition jc = plan.getJoinConditions().get(0);
            assertTrue(
                (jc.getLeftTable().equalsIgnoreCase("employees") && jc.getRightTable().equalsIgnoreCase("departments")) ||
                (jc.getLeftTable().equalsIgnoreCase("departments") && jc.getRightTable().equalsIgnoreCase("employees"))
            );
            assertEquals("department_id", jc.getLeftColumn());
            assertEquals("department_id", jc.getRightColumn());

            assertFalse(plan.getFilterConditions().isEmpty());
            FilterCondition fc = plan.getFilterConditions().get(0);
            assertEquals("departments", fc.getTableName());
            assertEquals("department_name", fc.getColumnName());
            assertEquals("Engineering", fc.getValue());
        }
    }

    @Test
    @DisplayName("6. Chemistry DB - Simple SELECT: List all reagents")
    public void testChemistryListAllReagents() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(CHEM_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "List all reagents";
            QueryPlan plan = planner.understand(question, schema);

            printPlanHeader("TEST 6: CHEMISTRY DB - LIST ALL REAGENTS", plan);

            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertEquals(QueryIntent.SELECT, plan.getIntent());
            assertTrue(plan.getTargetTables().contains("reagents"));
            assertEquals(1, plan.getSelectFields().size());
        }
    }

    @Test
    @DisplayName("7. Chemistry DB - Aggregation: How many tests are there?")
    public void testChemistryCountTests() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(CHEM_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "How many tests are there?";
            QueryPlan plan = planner.understand(question, schema);

            printPlanHeader("TEST 7: CHEMISTRY DB - COUNT TESTS", plan);

            assertEquals(QueryPlanStatus.SUPPORTED, plan.getStatus());
            assertEquals(QueryIntent.COUNT, plan.getIntent());
            assertTrue(plan.getTargetTables().contains("tests"));
            assertEquals(1, plan.getSelectFields().size());
            assertEquals(AggregationType.COUNT, plan.getSelectFields().get(0).getAggregationType());
        }
    }

    @Test
    @DisplayName("8. Ambiguity Test - Show names without table context")
    public void testAmbiguousColumnQuery() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Show names";
            QueryPlan plan = planner.understand(question, schema);

            printPlanHeader("TEST 8: AMBIGUITY TEST - SHOW NAMES", plan);

            assertEquals(QueryPlanStatus.AMBIGUOUS, plan.getStatus());
            assertNotNull(plan.getStatusReason());
        }
    }

    @Test
    @DisplayName("9. Unsupported Test - Out of domain question")
    public void testUnsupportedQuestion() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            String question = "Who won the 2024 World Cup?";
            QueryPlan plan = planner.understand(question, schema);

            printPlanHeader("TEST 9: UNSUPPORTED TEST - OUT OF DOMAIN", plan);

            assertEquals(QueryPlanStatus.UNSUPPORTED, plan.getStatus());
            assertNotNull(plan.getStatusReason());
        }
    }

    @Test
    @DisplayName("10. Database Switching - Validate same Planner operating on different schemas")
    public void testDatabaseSwitchingPlanner() throws Exception {
        try (DatabaseContext companyContext = DatabaseContext.open(COMPANY_DB_PATH);
             DatabaseContext chemContext = DatabaseContext.open(CHEM_DB_PATH)) {

            QueryPlan companyPlan = planner.understand("List all employees", companyContext.getSchemaMetadata());
            assertEquals(QueryPlanStatus.SUPPORTED, companyPlan.getStatus());
            assertTrue(companyPlan.getTargetTables().contains("employees"));

            QueryPlan chemPlan = planner.understand("List all reagents", chemContext.getSchemaMetadata());
            assertEquals(QueryPlanStatus.SUPPORTED, chemPlan.getStatus());
            assertTrue(chemPlan.getTargetTables().contains("reagents"));
        }
    }
}
