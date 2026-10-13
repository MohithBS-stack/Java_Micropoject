package com.nl2sql.database;

import com.nl2sql.metadata.SchemaMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 23: Multi-Database Support & Connection Abstraction Test.
 */
public class MultiDatabaseConnectionTest {

    private static final String COMPANY_DB_PATH = Paths.get("data", "company.db").toAbsolutePath().toString();

    @Test
    @DisplayName("1. Open database via direct file path")
    public void testOpenFileDatabase() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            assertNotNull(context.getSchemaMetadata());
            assertTrue(context.getSchemaMetadata().hasTable("employees"));
            assertTrue(context.getJdbcUrl().startsWith("jdbc:sqlite:"));

            try (Connection conn = context.getConnection()) {
                assertNotNull(conn);
                assertFalse(conn.isClosed());
            }
        }
    }

    @Test
    @DisplayName("2. Open database via generic JDBC URL")
    public void testOpenJdbcDatabase() throws Exception {
        String jdbcUrl = "jdbc:sqlite:" + COMPANY_DB_PATH;
        try (DatabaseContext context = DatabaseContext.open(jdbcUrl)) {
            assertEquals(jdbcUrl, context.getJdbcUrl());
            SchemaMetadata schema = context.getSchemaMetadata();
            assertNotNull(schema);
            assertTrue(schema.hasTable("departments"));
        }
    }

    @Test
    @DisplayName("3. Dynamic schema refresh")
    public void testDynamicSchemaRefresh() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            SchemaMetadata refreshed = context.refreshSchema();
            assertNotNull(refreshed);
            assertTrue(refreshed.getTables().size() >= 2);
        }
    }
}
