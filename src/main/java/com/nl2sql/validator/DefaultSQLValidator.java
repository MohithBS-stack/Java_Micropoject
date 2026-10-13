package com.nl2sql.validator;

import com.nl2sql.generator.GeneratedQuery;
import com.nl2sql.metadata.SchemaMetadata;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Independent SQL validator enforcing execution safety, injection defense, and catalog schema compliance.
 */
public class DefaultSQLValidator implements SQLValidator {

    private static final String[] FORBIDDEN_VERBS = {
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
            "ATTACH", "DETACH", "PRAGMA", "REPLACE", "TRUNCATE", "VACUUM"
    };

    @Override
    public SQLValidationResult validate(GeneratedQuery query, SchemaMetadata schemaMetadata) {
        // 1. Verify query generation success status
        if (query == null) {
            return SQLValidationResult.failure("Query object is null");
        }
        if (!query.isSuccess()) {
            return SQLValidationResult.failure("Query generation failed or required clarification: " + query.getErrorMessage());
        }

        String rawSql = query.getSql();
        if (rawSql == null || rawSql.trim().isEmpty()) {
            return SQLValidationResult.failure("SQL text is null or empty");
        }

        // 2. Reject SQL comments (-- or /* */)
        if (rawSql.contains("--") || rawSql.contains("/*") || rawSql.contains("*/")) {
            return SQLValidationResult.failure("Security Violation: SQL comments (-- or /* */) are prohibited");
        }

        // 3. Reject multiple statements (semicolons)
        String trimmedSql = rawSql.trim();
        if (trimmedSql.endsWith(";")) {
            trimmedSql = trimmedSql.substring(0, trimmedSql.length() - 1).trim();
        }
        if (trimmedSql.contains(";")) {
            return SQLValidationResult.failure("Security Violation: Multiple SQL statements are prohibited");
        }

        // 4. Reject non-SELECT queries and forbidden verbs
        String upperSql = trimmedSql.toUpperCase();
        if (!upperSql.startsWith("SELECT")) {
            return SQLValidationResult.failure("Security Violation: Only SELECT queries are permitted");
        }

        for (String verb : FORBIDDEN_VERBS) {
            if (Pattern.compile("\\b" + verb + "\\b").matcher(upperSql).find()) {
                return SQLValidationResult.failure("Security Violation: Forbidden SQL operation/verb detected: " + verb);
            }
        }

        // 5. Verify parameter count matches number of '?' placeholders
        int placeholderCount = countOccurrences(trimmedSql, '?');
        int parameterCount = query.getParameters() != null ? query.getParameters().size() : 0;
        if (placeholderCount != parameterCount) {
            return SQLValidationResult.failure("Parameter Mismatch: SQL contains " + placeholderCount +
                    " '?' placeholder(s) but " + parameterCount + " parameter(s) were supplied");
        }

        // 6. Schema Verification (Tables & Columns)
        if (schemaMetadata == null) {
            return SQLValidationResult.failure("Schema Violation: SchemaMetadata is null");
        }

        Map<String, String> aliasToTableMap = new HashMap<>();
        Set<String> validatedTables = new LinkedHashSet<>();
        Set<String> validatedColumns = new LinkedHashSet<>();

        // Extract FROM and JOIN tables with aliases
        Pattern tablePattern = Pattern.compile("\\b(?:FROM|JOIN)\\s+([a-zA-Z0-9_]+)(?:\\s+(?:AS\\s+)?([a-zA-Z0-9_]+))?", Pattern.CASE_INSENSITIVE);
        Matcher tableMatcher = tablePattern.matcher(trimmedSql);

        while (tableMatcher.find()) {
            String tableName = tableMatcher.group(1);
            String alias = tableMatcher.group(2);

            // Ignore keywords mistakenly captured if any
            if (isSqlKeyword(tableName)) {
                continue;
            }

            if (!schemaMetadata.hasTable(tableName)) {
                return SQLValidationResult.failure("Schema Violation: Referenced table does not exist in database catalog: " + tableName);
            }

            validatedTables.add(tableName.toLowerCase());
            aliasToTableMap.put(tableName.toLowerCase(), tableName.toLowerCase());
            if (alias != null && !isSqlKeyword(alias)) {
                aliasToTableMap.put(alias.toLowerCase(), tableName.toLowerCase());
            }
        }

        if (validatedTables.isEmpty()) {
            return SQLValidationResult.failure("Schema Violation: Could not identify target table in SELECT query");
        }

        // Extract qualified columns: alias.column_name
        Pattern qualifiedColPattern = Pattern.compile("\\b([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)\\b");
        Matcher colMatcher = qualifiedColPattern.matcher(trimmedSql);

        while (colMatcher.find()) {
            String alias = colMatcher.group(1).toLowerCase();
            String colName = colMatcher.group(2).toLowerCase();

            if (aliasToTableMap.containsKey(alias)) {
                String targetTable = aliasToTableMap.get(alias);
                if (!schemaMetadata.hasColumn(targetTable, colName)) {
                    return SQLValidationResult.failure("Schema Violation: Column '" + colName + "' does not exist in table '" + targetTable + "'");
                }
                validatedColumns.add(targetTable + "." + colName);
            }
        }

        // Extract unqualified columns in simple queries (e.g. SELECT fake_column FROM tests)
        if (validatedTables.size() == 1) {
            String singleTable = validatedTables.iterator().next();
            Pattern selectColPattern = Pattern.compile("\\bSELECT\\s+([a-zA-Z0-9_,\\s]+)\\s+FROM\\b", Pattern.CASE_INSENSITIVE);
            Matcher selectColMatcher = selectColPattern.matcher(trimmedSql);
            if (selectColMatcher.find()) {
                String colListStr = selectColMatcher.group(1);
                String[] rawCols = colListStr.split(",");
                for (String rawCol : rawCols) {
                    String col = rawCol.trim().replaceAll("(?i)^DISTINCT\\s+", "");
                    // Strip aggregate wrapper if present (e.g. AVG(salary) -> salary)
                    String innerCol = col.replaceAll("(?i)^(?:COUNT|AVG|SUM|MIN|MAX)\\s*\\((.*)\\)$", "$1").trim();
                    if (!innerCol.contains(".") && !innerCol.equals("*") && !isSqlKeyword(innerCol)) {
                        if (!schemaMetadata.hasColumn(singleTable, innerCol)) {
                            return SQLValidationResult.failure("Schema Violation: Column '" + innerCol + "' does not exist in table '" + singleTable + "'");
                        }
                        validatedColumns.add(singleTable + "." + innerCol.toLowerCase());
                    }
                }
            }
        }

        return SQLValidationResult.success(validatedTables, validatedColumns);
    }

    private int countOccurrences(String str, char c) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    private boolean isSqlKeyword(String word) {
        if (word == null) return true;
        Set<String> keywords = Set.of(
                "AS", "ON", "WHERE", "ORDER", "BY", "ASC", "DESC", "AND", "OR",
                "JOIN", "INNER", "LEFT", "RIGHT", "SELECT", "FROM", "GROUP", "HAVING",
                "COUNT", "SUM", "AVG", "MIN", "MAX", "LIMIT", "DISTINCT", "IS", "NULL", "NOT"
        );
        return keywords.contains(word.toUpperCase());
    }
}
