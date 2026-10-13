package com.nl2sql.database;

import com.nl2sql.executor.DatabaseExecutor;
import com.nl2sql.executor.DefaultDatabaseExecutor;
import com.nl2sql.executor.QueryResult;
import com.nl2sql.generator.GeneratedQuery;
import com.nl2sql.metadata.ColumnMetadata;
import com.nl2sql.metadata.ForeignKeyMetadata;
import com.nl2sql.metadata.SchemaPrinter;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.metadata.TableMetadata;
import com.nl2sql.validator.DefaultSQLValidator;
import com.nl2sql.validator.SQLValidationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 14: Universal Database Foundation Test Suite.
 * Validates automatic schema discovery, generic DatabaseContext, database switching,
 * composite primary keys, and schema inspection formatting across arbitrary SQLite databases.
 */
public class UniversalDatabaseContextTest {

    private static final String CHEM_DB_PATH = "data/chemistry.db";
    private static final String COMPANY_DB_PATH = "data/company.db";

    @BeforeAll
    public static void setUp() throws Exception {
        // Ensure synthetic company database exists for tests
        CompanyDatabaseCreator.createCompanyDatabase(COMPANY_DB_PATH);
    }

    @Test
    @DisplayName("1. Automatic Schema Discovery - Company Database (company.db)")
    public void testCompanySchemaDiscovery() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            assertEquals(COMPANY_DB_PATH, context.getDbPath());
            assertTrue(context.getJdbcUrl().startsWith("jdbc:sqlite:"));

            SchemaMetadata schema = context.getSchemaMetadata();
            assertNotNull(schema, "SchemaMetadata must be discovered automatically upon context open");

            // Verify table discovery
            assertTrue(schema.hasTable("departments"), "Catalog must contain 'departments'");
            assertTrue(schema.hasTable("employees"), "Catalog must contain 'employees'");

            // Verify departments columns
            TableMetadata deptsTable = schema.getTable("departments");
            assertNotNull(deptsTable);
            assertTrue(deptsTable.hasColumn("department_id"));
            assertTrue(deptsTable.hasColumn("department_name"));

            ColumnMetadata deptIdCol = deptsTable.getColumn("department_id");
            assertTrue(deptIdCol.isPrimaryKey(), "department_id must be primary key");

            // Verify employees columns & types
            TableMetadata empTable = schema.getTable("employees");
            assertNotNull(empTable);
            assertTrue(empTable.hasColumn("employee_id"));
            assertTrue(empTable.hasColumn("employee_name"));
            assertTrue(empTable.hasColumn("department_id"));
            assertTrue(empTable.hasColumn("salary"));
            assertTrue(empTable.hasColumn("hire_date"));

            ColumnMetadata empIdCol = empTable.getColumn("employee_id");
            assertTrue(empIdCol.isPrimaryKey(), "employee_id must be primary key");

            // Verify Foreign Key relationship
            List<ForeignKeyMetadata> fks = empTable.getForeignKeys();
            assertFalse(fks.isEmpty(), "employees table must have foreign key constraint");
            ForeignKeyMetadata fk = fks.get(0);
            assertEquals("department_id", fk.getFkColumnName().toLowerCase());
            assertEquals("departments", fk.getPkTableName().toLowerCase());
            assertEquals("department_id", fk.getPkColumnName().toLowerCase());
        }
    }

    @Test
    @DisplayName("2. Automatic Schema Discovery - Chemistry Database (chemistry.db)")
    public void testChemistrySchemaDiscovery() throws Exception {
        File chemFile = new File(CHEM_DB_PATH);
        assertTrue(chemFile.exists(), "data/chemistry.db must exist for test execution");

        try (DatabaseContext context = DatabaseContext.open(CHEM_DB_PATH)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            assertNotNull(schema);

            assertTrue(schema.hasTable("analytes"));
            assertTrue(schema.hasTable("tests"));
            assertTrue(schema.hasTable("reagents"));
            assertTrue(schema.hasTable("test_reagents"));

            TableMetadata testsTable = schema.getTable("tests");
            assertNotNull(testsTable);
            assertTrue(testsTable.hasColumn("test_id"));
            assertTrue(testsTable.hasColumn("test_name"));

            ColumnMetadata pkCol = testsTable.getColumn("test_id");
            assertTrue(pkCol.isPrimaryKey());
        }
    }

    @Test
    @DisplayName("3. Database Switching - Open both company.db and chemistry.db sequentially")
    public void testDatabaseSwitching() throws Exception {
        // Open Company Context
        try (DatabaseContext companyContext = DatabaseContext.open(COMPANY_DB_PATH)) {
            assertTrue(companyContext.getSchemaMetadata().hasTable("employees"));
            assertFalse(companyContext.getSchemaMetadata().hasTable("analytes"));
        }

        // Open Chemistry Context
        try (DatabaseContext chemistryContext = DatabaseContext.open(CHEM_DB_PATH)) {
            assertTrue(chemistryContext.getSchemaMetadata().hasTable("analytes"));
            assertFalse(chemistryContext.getSchemaMetadata().hasTable("employees"));
        }
    }

    @Test
    @DisplayName("4. Generic Database Execution - Query company.db without domain logic")
    public void testGenericDatabaseExecutionOnCompanyDb() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            DatabaseExecutor executor = new DefaultDatabaseExecutor(context.getDatabaseManager());
            DefaultSQLValidator validator = new DefaultSQLValidator();

            GeneratedQuery query = new GeneratedQuery(
                    "SELECT e.employee_name, d.department_name, e.salary FROM employees e JOIN departments d ON e.department_id = d.department_id WHERE e.salary > ?",
                    List.of(80000.0),
                    List.of("employees", "departments"),
                    null
            );

            SQLValidationResult validation = validator.validate(query, context.getSchemaMetadata());
            assertTrue(validation.isValid(), "Generic validation must pass on company.db schema");

            QueryResult result = executor.execute(query, validation);
            assertTrue(result.isSuccess(), "Generic execution must succeed");
            assertTrue(result.getRowCount() >= 2, "Should return matching employees with salary > 80000");
            assertTrue(result.getColumnNames().contains("employee_name"));
        }
    }

    @Test
    @DisplayName("5. Composite Primary Key Discovery")
    public void testCompositePrimaryKeyDiscovery(@TempDir Path tempDir) throws Exception {
        Path tempDbPath = tempDir.resolve("composite_test.db");
        String dbPath = tempDbPath.toAbsolutePath().toString();

        try (DatabaseManager manager = new DatabaseManager(dbPath)) {
            Connection conn = manager.getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE order_items (" +
                        "order_id INTEGER, " +
                        "item_id INTEGER, " +
                        "quantity INTEGER, " +
                        "PRIMARY KEY (order_id, item_id)" +
                        ");");
            }
        }

        try (DatabaseContext context = DatabaseContext.open(dbPath)) {
            SchemaMetadata schema = context.getSchemaMetadata();
            TableMetadata orderItemsTable = schema.getTable("order_items");
            assertNotNull(orderItemsTable);

            assertTrue(orderItemsTable.hasCompositePrimaryKey(), "Table with two PK columns must indicate composite primary key");
            List<ColumnMetadata> pkCols = orderItemsTable.getPrimaryKeyColumns();
            assertEquals(2, pkCols.size(), "Should discover 2 primary key columns");

            List<String> pkColNames = pkCols.stream().map(ColumnMetadata::getName).toList();
            assertTrue(pkColNames.contains("order_id"));
            assertTrue(pkColNames.contains("item_id"));
        }
    }

    @Test
    @DisplayName("6. Schema Inspection Report Formatting - Phase 14 Format Compliance")
    public void testSchemaInspectionFormatting() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            String report = SchemaPrinter.formatSchemaInspection(context);

            assertNotNull(report);
            assertTrue(report.contains("DATABASE:"), "Report must contain DATABASE:");
            assertTrue(report.contains(COMPANY_DB_PATH), "Report must contain DB path");
            assertTrue(report.contains("TABLES:"), "Report must contain TABLES:");
            assertTrue(report.contains("- departments"), "Report must list departments");
            assertTrue(report.contains("- employees"), "Report must list employees");
            assertTrue(report.contains("TABLE: departments"), "Report must contain TABLE: departments");
            assertTrue(report.contains("COLUMNS:"), "Report must contain COLUMNS:");
            assertTrue(report.contains("department_id | INTEGER"), "Report must contain department_id column");
            assertTrue(report.contains("primary_key=true"), "Report must indicate primary key");
            assertTrue(report.contains("FOREIGN KEYS:"), "Report must contain FOREIGN KEYS:");
            assertTrue(report.contains("employees.department_id -> departments.department_id"), "Report must contain FK relationship");
        }
    }
}
