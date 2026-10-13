package com.nl2sql.generator;

import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.nlp.Entity;
import com.nl2sql.nlp.Intent;
import com.nl2sql.nlp.ParsedQuestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of SQLQueryGenerator.
 * Converts ParsedQuestion objects into parameterized SQL SELECT queries validated against SchemaMetadata.
 */
public class DefaultSQLQueryGenerator implements SQLQueryGenerator {

    @Override
    public GeneratedQuery generate(ParsedQuestion parsedQuestion, SchemaMetadata schemaMetadata) {
        if (parsedQuestion == null) {
            return new GeneratedQuery(Intent.UNKNOWN, "Null parsed question provided");
        }

        // 1. Refuse generation if clarification is required or intent is UNKNOWN
        if (parsedQuestion.isClarificationRequired()) {
            return new GeneratedQuery(parsedQuestion.getIntent(),
                    "Cannot generate SQL: Clarification required (" + parsedQuestion.getClarificationReason() + ")");
        }

        if (parsedQuestion.getIntent() == Intent.UNKNOWN) {
            return new GeneratedQuery(Intent.UNKNOWN, "Cannot generate SQL for UNKNOWN intent");
        }

        Intent intent = parsedQuestion.getIntent();

        // 2. Dispatch to intent-specific query builder
        switch (intent) {
            case FIND_REAGENTS:
                return buildFindReagentsQuery(parsedQuestion, schemaMetadata);
            case FIND_OBSERVATION:
                return buildFindObservationQuery(parsedQuestion, schemaMetadata);
            case FIND_PROCEDURE:
                return buildFindProcedureQuery(parsedQuestion, schemaMetadata);
            case FIND_TEST:
                return buildFindTestQuery(parsedQuestion, schemaMetadata);
            case COUNT_RECORDS:
                return buildCountRecordsQuery(parsedQuestion, schemaMetadata);
            default:
                return new GeneratedQuery(intent, "Unsupported intent: " + intent);
        }
    }

    /**
     * Builds query for FIND_REAGENTS: SELECT r.name, r.formula, tr.addition_order, tr.quantity_notes ...
     */
    private GeneratedQuery buildFindReagentsQuery(ParsedQuestion parsedQuestion, SchemaMetadata schema) {
        if (parsedQuestion.getEntities().isEmpty()) {
            List<String> requiredTables = List.of("reagents");
            if (!verifyTablesExist(requiredTables, schema)) {
                return new GeneratedQuery(Intent.FIND_REAGENTS, "Schema error: Table 'reagents' does not exist in database");
            }
            String sql = "SELECT r.reagent_id, r.name AS reagent_name, r.formula, r.description FROM reagents r ORDER BY r.reagent_id ASC";
            return finalizeQuery(sql, List.of(), requiredTables, Intent.FIND_REAGENTS);
        }

        List<String> requiredTables = List.of("analytes", "tests", "test_reagents", "reagents");
        if (!verifyTablesExist(requiredTables, schema)) {
            return new GeneratedQuery(Intent.FIND_REAGENTS, "Schema error: Required tables for FIND_REAGENTS do not exist in database");
        }

        String selectClause = "SELECT r.name AS reagent_name, r.formula, tr.addition_order, tr.quantity_notes " +
                "FROM reagents r " +
                "JOIN test_reagents tr ON r.reagent_id = tr.reagent_id " +
                "JOIN tests t ON tr.test_id = t.test_id " +
                "JOIN analytes a ON t.analyte_id = a.analyte_id";

        WhereClauseResult where = buildWhereClause(parsedQuestion);
        String sql = selectClause + where.sqlClause + " ORDER BY tr.addition_order ASC";

        return finalizeQuery(sql, where.parameters, requiredTables, Intent.FIND_REAGENTS);
    }

    /**
     * Builds query for FIND_OBSERVATION: SELECT o.expected_result, o.inference ...
     */
    private GeneratedQuery buildFindObservationQuery(ParsedQuestion parsedQuestion, SchemaMetadata schema) {
        if (schema != null && schema.hasTable("salt_tests")) {
            List<String> requiredTables = List.of("analytes", "tests", "salt_tests");
            if (!verifyTablesExist(requiredTables, schema)) {
                return new GeneratedQuery(Intent.FIND_OBSERVATION, "Schema error: Required tables for FIND_OBSERVATION do not exist in database");
            }

            String selectClause = "SELECT st.expected_result, st.expected_observation, st.inference " +
                    "FROM salt_tests st " +
                    "JOIN tests t ON st.test_id = t.test_id " +
                    "JOIN analytes a ON t.analyte_id = a.analyte_id";

            WhereClauseResult where = buildWhereClause(parsedQuestion);
            String sql = selectClause + where.sqlClause;

            return finalizeQuery(sql, where.parameters, requiredTables, Intent.FIND_OBSERVATION);
        }

        List<String> requiredTables = List.of("analytes", "tests", "test_observations");
        if (!verifyTablesExist(requiredTables, schema)) {
            return new GeneratedQuery(Intent.FIND_OBSERVATION, "Schema error: Required tables for FIND_OBSERVATION do not exist in database");
        }

        String selectClause = "SELECT o.expected_result, o.inference " +
                "FROM test_observations o " +
                "JOIN tests t ON o.test_id = t.test_id " +
                "JOIN analytes a ON t.analyte_id = a.analyte_id";

        WhereClauseResult where = buildWhereClause(parsedQuestion);
        String sql = selectClause + where.sqlClause;

        return finalizeQuery(sql, where.parameters, requiredTables, Intent.FIND_OBSERVATION);
    }

    /**
     * Builds query for FIND_PROCEDURE: SELECT t.test_name, t.procedure_description ...
     */
    private GeneratedQuery buildFindProcedureQuery(ParsedQuestion parsedQuestion, SchemaMetadata schema) {
        List<String> requiredTables = List.of("analytes", "tests");
        if (!verifyTablesExist(requiredTables, schema)) {
            return new GeneratedQuery(Intent.FIND_PROCEDURE, "Schema error: Required tables for FIND_PROCEDURE do not exist in database");
        }

        String selectClause = "SELECT t.procedure_description, t.test_name " +
                "FROM tests t " +
                "JOIN analytes a ON t.analyte_id = a.analyte_id";

        WhereClauseResult where = buildWhereClause(parsedQuestion);
        String sql = selectClause + where.sqlClause;

        return finalizeQuery(sql, where.parameters, requiredTables, Intent.FIND_PROCEDURE);
    }

    /**
     * Builds query for FIND_TEST: SELECT t.test_id, t.test_name, t.test_type, t.sequence_number, t.procedure_description ...
     */
    private GeneratedQuery buildFindTestQuery(ParsedQuestion parsedQuestion, SchemaMetadata schema) {
        boolean hasReagentEntity = parsedQuestion.getEntities().stream().anyMatch(e -> e.getType().equals("reagent"));
        List<String> requiredTables = hasReagentEntity ? List.of("tests", "test_reagents", "reagents") : List.of("analytes", "tests");
        if (!verifyTablesExist(requiredTables, schema)) {
            return new GeneratedQuery(Intent.FIND_TEST, "Schema error: Required tables for FIND_TEST do not exist in database");
        }

        String selectClause = hasReagentEntity
                ? "SELECT t.test_id, t.test_name, t.test_type, t.sequence_number, t.procedure_description, a.name AS analyte_name FROM tests t LEFT JOIN analytes a ON t.analyte_id = a.analyte_id JOIN test_reagents tr ON t.test_id = tr.test_id JOIN reagents r ON tr.reagent_id = r.reagent_id"
                : "SELECT t.test_id, t.test_name, t.test_type, t.sequence_number, t.procedure_description, a.name AS analyte_name FROM tests t JOIN analytes a ON t.analyte_id = a.analyte_id";

        WhereClauseResult where = buildWhereClause(parsedQuestion);
        String sql = selectClause + where.sqlClause;

        return finalizeQuery(sql, where.parameters, requiredTables, Intent.FIND_TEST);
    }

    /**
     * Builds query for COUNT_RECORDS: SELECT COUNT(*) AS total_count ...
     */
    private GeneratedQuery buildCountRecordsQuery(ParsedQuestion parsedQuestion, SchemaMetadata schema) {
        List<String> requiredTables = List.of("analytes", "tests");
        if (!verifyTablesExist(requiredTables, schema)) {
            return new GeneratedQuery(Intent.COUNT_RECORDS, "Schema error: Required tables for COUNT_RECORDS do not exist in database");
        }

        String selectClause = "SELECT COUNT(*) AS total_count " +
                "FROM tests t " +
                "JOIN analytes a ON t.analyte_id = a.analyte_id";

        WhereClauseResult where = buildWhereClause(parsedQuestion);
        String sql = selectClause + where.sqlClause;

        return finalizeQuery(sql, where.parameters, requiredTables, Intent.COUNT_RECORDS);
    }

    /**
     * Constructs parameterized WHERE clause based on extracted entities.
     */
    private WhereClauseResult buildWhereClause(ParsedQuestion parsedQuestion) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

        for (Entity entity : parsedQuestion.getEntities()) {
            switch (entity.getType()) {
                case "analyte_type":
                    conditions.add("a.analyte_type = ?");
                    parameters.add(entity.getValue());
                    break;
                case "sequence_number":
                    conditions.add("t.sequence_number = ?");
                    parameters.add(entity.getValue());
                    break;
                case "test_type":
                    conditions.add("LOWER(t.test_type) LIKE LOWER(?)");
                    parameters.add("%" + entity.getValue() + "%");
                    break;
                case "analyte":
                    conditions.add("LOWER(a.name) LIKE LOWER(?)");
                    parameters.add("%" + entity.getValue() + "%");
                    break;
                case "reagent":
                    conditions.add("LOWER(r.name) LIKE LOWER(?)");
                    parameters.add("%" + entity.getValue() + "%");
                    break;
                default:
                    // Unknown entity type ignored in WHERE clause
                    break;
            }
        }

        if (conditions.isEmpty()) {
            return new WhereClauseResult("", parameters);
        }

        String sqlClause = " WHERE " + String.join(" AND ", conditions);
        return new WhereClauseResult(sqlClause, parameters);
    }

    /**
     * Verifies that all required tables exist in SchemaMetadata.
     */
    private boolean verifyTablesExist(List<String> tables, SchemaMetadata schema) {
        if (schema == null) {
            return false;
        }
        for (String table : tables) {
            if (!schema.hasTable(table)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finalizes and validates generated SQL string before creating GeneratedQuery.
     */
    private GeneratedQuery finalizeQuery(String sql, List<Object> parameters, List<String> referencedTables, Intent intent) {
        // Security check: Must start with SELECT
        if (!sql.trim().toUpperCase().startsWith("SELECT")) {
            return new GeneratedQuery(intent, "Security Violation: Only SELECT queries are permitted");
        }

        // Security check: Prevent stacked queries or DDL/DML tokens
        String upper = sql.toUpperCase();
        if (upper.contains(";") || upper.contains("INSERT") || upper.contains("UPDATE") ||
                upper.contains("DELETE") || upper.contains("DROP") || upper.contains("ALTER") ||
                upper.contains("CREATE") || upper.contains("PRAGMA")) {
            return new GeneratedQuery(intent, "Security Violation: Unsafe SQL token detected");
        }

        return new GeneratedQuery(sql, parameters, referencedTables, intent);
    }

    private static class WhereClauseResult {
        final String sqlClause;
        final List<Object> parameters;

        WhereClauseResult(String sqlClause, List<Object> parameters) {
            this.sqlClause = sqlClause;
            this.parameters = parameters;
        }
    }
}
