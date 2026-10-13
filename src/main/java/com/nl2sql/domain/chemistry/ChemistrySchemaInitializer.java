package com.nl2sql.domain.chemistry;

import com.nl2sql.database.DatabaseManager;
import com.nl2sql.database.SchemaInitializer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Chemistry Demonstration Domain Schema Initializer.
 * Encapsulates table creation and non-chemical placeholder seeding for the chemistry lab demo.
 */
public class ChemistrySchemaInitializer implements SchemaInitializer {

    @Override
    public void initializeSchema(DatabaseManager databaseManager) throws SQLException {
        Connection conn = databaseManager.getConnection();

        try (Statement stmt = conn.createStatement()) {
            // 1. Analytes table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS analytes (
                    analyte_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    symbol_formula TEXT,
                    charge TEXT,
                    notes TEXT,
                    analyte_type TEXT NOT NULL
                );
            """);

            // 2. Tests table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tests (
                    test_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    analyte_id INTEGER,
                    test_name TEXT NOT NULL,
                    test_type TEXT,
                    sequence_number INTEGER DEFAULT 1,
                    procedure_description TEXT,
                    FOREIGN KEY (analyte_id) REFERENCES analytes (analyte_id) ON DELETE CASCADE
                );
            """);

            // 3. Reagents table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS reagents (
                    reagent_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    formula TEXT,
                    description TEXT
                );
            """);

            // 4. Test Reagents Junction table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS test_reagents (
                    test_id INTEGER NOT NULL,
                    reagent_id INTEGER NOT NULL,
                    addition_order INTEGER DEFAULT 1,
                    quantity_notes TEXT,
                    PRIMARY KEY (test_id, reagent_id, addition_order),
                    FOREIGN KEY (test_id) REFERENCES tests (test_id) ON DELETE CASCADE,
                    FOREIGN KEY (reagent_id) REFERENCES reagents (reagent_id) ON DELETE CASCADE
                );
            """);

            // 5. Test Observations table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS test_observations (
                    observation_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    test_id INTEGER NOT NULL,
                    expected_result TEXT NOT NULL,
                    inference TEXT,
                    FOREIGN KEY (test_id) REFERENCES tests (test_id) ON DELETE CASCADE
                );
            """);
        }
    }

    @Override
    public List<String> getExistingTables(DatabaseManager databaseManager) throws SQLException {
        List<String> tables = new ArrayList<>();
        Connection conn = databaseManager.getConnection();

        String query = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%';";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }
        }
        return tables;
    }

    @Override
    public void seedPlaceholdersIfEmpty(DatabaseManager databaseManager) throws SQLException {
        Connection conn = databaseManager.getConnection();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM analytes;")) {
            if (rs.next() && rs.getInt("cnt") > 0) {
                return; // Already seeded
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                INSERT INTO analytes (analyte_id, name, symbol_formula, analyte_type)
                VALUES (1, 'ANION-PLACEHOLDER-1', 'A1-', 'ANION');
            """);

            stmt.execute("""
                INSERT INTO tests (test_id, analyte_id, test_name, test_type, sequence_number, procedure_description)
                VALUES (1, 1, 'TEST-PLACEHOLDER-1', 'PRELIMINARY', 1, 'Procedure description for placeholder test 1.');
            """);

            stmt.execute("""
                INSERT INTO reagents (reagent_id, name, formula, description)
                VALUES (1, 'REAGENT-PLACEHOLDER-1', 'R1', 'Description for placeholder reagent 1.');
            """);

            stmt.execute("""
                INSERT INTO test_reagents (test_id, reagent_id, addition_order, quantity_notes)
                VALUES (1, 1, 1, 'Add 1 mL of REAGENT-PLACEHOLDER-1');
            """);

            stmt.execute("""
                INSERT INTO test_observations (observation_id, test_id, expected_result, inference)
                VALUES (1, 1, 'OBSERVATION-PLACEHOLDER-1', 'Inference for placeholder test 1');
            """);
        }
    }
}
