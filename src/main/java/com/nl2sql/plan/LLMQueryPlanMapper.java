package com.nl2sql.plan;

import com.nl2sql.metadata.ForeignKeyMetadata;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.metadata.TableMetadata;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safely parses and validates JSON responses from LLM into a structured QueryPlan.
 * Verifies every referenced table, column, and relationship against SchemaMetadata.
 * Rejects raw SQL injection attempts, hallucinated schema objects, invalid joins, and invalid operators.
 */
public class LLMQueryPlanMapper {

    public static QueryPlan parseAndValidate(String rawResponse, String question, SchemaMetadata schema) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return QueryPlan.unsupported(question, "LLM returned empty or null response.");
        }

        String cleanedText = extractJsonBlock(rawResponse);

        // Security check: Reject any raw SQL output from the LLM
        if (Pattern.compile("\"sql\"\\s*:", Pattern.CASE_INSENSITIVE).matcher(cleanedText).find()) {
            return QueryPlan.unsupported(question, "Security Rejection: LLM attempted to output raw SQL.");
        }

        // Basic validation: ensure proper JSON object framing and status field
        if (!cleanedText.startsWith("{") || !cleanedText.endsWith("}") || !cleanedText.matches("(?s).*\"status\"\\s*:\\s*\"[A-Z_]+\".*")) {
            throw new IllegalArgumentException("Malformed LLM response: invalid JSON structure or missing required 'status' field.");
        }

        // Parse Status
        QueryPlanStatus status = extractStatus(cleanedText);
        String statusReason = extractStatusReason(cleanedText);

        if (status == QueryPlanStatus.UNSUPPORTED) {
            return QueryPlan.unsupported(question, statusReason != null ? statusReason : "Query is unsupported according to LLM analysis.");
        }
        if (status == QueryPlanStatus.AMBIGUOUS) {
            return QueryPlan.ambiguous(question, statusReason != null ? statusReason : "Query is ambiguous according to LLM analysis.");
        }

        // Parse Intent
        QueryIntent intent = extractIntent(cleanedText);

        // Parse Target Tables (with fallback inference from referenced tables in JSON)
        List<String> targetTables = extractListField(cleanedText, "targetTables", "tables");
        if (targetTables.isEmpty()) {
            targetTables = inferReferencedTables(cleanedText, schema);
        }
        if (targetTables.isEmpty()) {
            return QueryPlan.unsupported(question, "Validation Error: LLM plan contains no target tables or valid schema references.");
        }

        // Schema validation on target tables (Hallucination protection)
        for (String tbl : targetTables) {
            if (!schema.hasTable(tbl)) {
                return QueryPlan.unsupported(question, "Hallucination Defense: Referenced nonexistent table '" + tbl + "'");
            }
        }

        QueryPlan.Builder builder = new QueryPlan.Builder(question)
                .status(QueryPlanStatus.SUPPORTED)
                .statusReason(statusReason)
                .intent(intent);

        for (String tbl : targetTables) {
            builder.addTargetTable(tbl);
        }

        // Parse Distinct & Limit
        boolean distinct = extractBoolean(cleanedText, "distinct");
        builder.distinct(distinct);

        Integer limit = extractInteger(cleanedText, "limit");
        if (limit != null && limit > 0) {
            builder.limit(limit);
        }

        String primaryTable = targetTables.get(0);

        // Parse and validate Select Fields
        SelectFieldsResult sfResult = extractSelectFields(cleanedText, primaryTable, schema);
        if (!sfResult.success) {
            return QueryPlan.unsupported(question, sfResult.errorMessage);
        }
        for (SelectField sf : sfResult.fields) {
            builder.addSelectField(sf);
        }

        // Parse and validate Filter Conditions
        FilterConditionsResult fcResult = extractFilterConditions(cleanedText, primaryTable, schema);
        if (!fcResult.success) {
            return QueryPlan.unsupported(question, fcResult.errorMessage);
        }
        for (FilterCondition fc : fcResult.conditions) {
            builder.addFilterCondition(fc);
        }

        // Parse and validate Join Conditions
        JoinConditionsResult jcResult = extractJoinConditions(cleanedText, schema);
        if (!jcResult.success) {
            return QueryPlan.unsupported(question, jcResult.errorMessage);
        }
        for (JoinCondition jc : jcResult.joins) {
            builder.addJoinCondition(jc);
        }

        // Parse and validate Order Specifications
        OrderSpecificationsResult osResult = extractOrderSpecifications(cleanedText, primaryTable, schema);
        if (!osResult.success) {
            return QueryPlan.unsupported(question, osResult.errorMessage);
        }
        for (OrderSpecification os : osResult.specs) {
            builder.addOrderSpecification(os);
        }

        // Parse and validate Group By Fields
        GroupByFieldsResult gbResult = extractGroupByFields(cleanedText, primaryTable, schema);
        if (!gbResult.success) {
            return QueryPlan.unsupported(question, gbResult.errorMessage);
        }
        for (SelectField sf : gbResult.fields) {
            builder.addGroupByField(sf);
        }

        // Parse and validate Having Conditions
        HavingConditionsResult hvResult = extractHavingConditions(cleanedText, primaryTable, schema);
        if (!hvResult.success) {
            return QueryPlan.unsupported(question, hvResult.errorMessage);
        }
        for (HavingCondition hc : hvResult.conditions) {
            builder.addHavingCondition(hc);
        }

        return builder.build();
    }

    private static String extractJsonBlock(String text) {
        if (text == null) return "";
        if (text.contains("\"parts\":")) {
            Pattern pParts = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"");
            Matcher mParts = pParts.matcher(text);
            if (mParts.find()) {
                text = mParts.group(1).replace("\\n", "\n").replace("\\\"", "\"");
            }
        }
        if (text.contains("```json")) {
            int start = text.indexOf("```json") + 7;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        } else if (text.contains("```")) {
            int start = text.indexOf("```") + 3;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return text.trim();
    }

    private static QueryPlanStatus extractStatus(String json) {
        Pattern p = Pattern.compile("\"status\"\\s*:\\s*\"([A-Z_]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return QueryPlanStatus.valueOf(m.group(1).toUpperCase());
            } catch (Exception ignored) {}
        }
        return QueryPlanStatus.SUPPORTED;
    }

    private static String extractStatusReason(String json) {
        Pattern p = Pattern.compile("\"statusReason\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static QueryIntent extractIntent(String json) {
        Pattern p = Pattern.compile("\"intent\"\\s*:\\s*\"([A-Z_]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return QueryIntent.valueOf(m.group(1).toUpperCase());
            } catch (Exception ignored) {}
        }
        return QueryIntent.SELECT;
    }

    private static List<String> extractListField(String json, String... keys) {
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(json);
            if (m.find()) {
                String arrayContent = m.group(1);
                Matcher elemMatcher = Pattern.compile("\"([^\"]+)\"").matcher(arrayContent);
                while (elemMatcher.find()) {
                    result.add(elemMatcher.group(1));
                }
                if (!result.isEmpty()) return result;
            }
        }
        return result;
    }

    private static List<String> inferReferencedTables(String json, SchemaMetadata schema) {
        Set<String> tables = new LinkedHashSet<>();
        Pattern p = Pattern.compile("\"(?:tableName|table|leftTable|rightTable)\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        while (m.find()) {
            String tbl = m.group(1);
            if (schema.hasTable(tbl)) {
                tables.add(tbl);
            }
        }
        return new ArrayList<>(tables);
    }

    private static Boolean extractBoolean(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1));
        }
        return false;
    }

    private static Integer extractInteger(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    // --- Validation & extraction result containers ---

    private static class SelectFieldsResult {
        final boolean success;
        final List<SelectField> fields;
        final String errorMessage;

        SelectFieldsResult(List<SelectField> fields) {
            this.success = true;
            this.fields = fields;
            this.errorMessage = null;
        }

        SelectFieldsResult(String errorMessage) {
            this.success = false;
            this.fields = Collections.emptyList();
            this.errorMessage = errorMessage;
        }
    }

    private static class FilterConditionsResult {
        final boolean success;
        final List<FilterCondition> conditions;
        final String errorMessage;

        FilterConditionsResult(List<FilterCondition> conditions) {
            this.success = true;
            this.conditions = conditions;
            this.errorMessage = null;
        }

        FilterConditionsResult(String errorMessage) {
            this.success = false;
            this.conditions = Collections.emptyList();
            this.errorMessage = errorMessage;
        }
    }

    private static class JoinConditionsResult {
        final boolean success;
        final List<JoinCondition> joins;
        final String errorMessage;

        JoinConditionsResult(List<JoinCondition> joins) {
            this.success = true;
            this.joins = joins;
            this.errorMessage = null;
        }

        JoinConditionsResult(String errorMessage) {
            this.success = false;
            this.joins = Collections.emptyList();
            this.errorMessage = errorMessage;
        }
    }

    private static class OrderSpecificationsResult {
        final boolean success;
        final List<OrderSpecification> specs;
        final String errorMessage;

        OrderSpecificationsResult(List<OrderSpecification> specs) {
            this.success = true;
            this.specs = specs;
            this.errorMessage = null;
        }

        OrderSpecificationsResult(String errorMessage) {
            this.success = false;
            this.specs = Collections.emptyList();
            this.errorMessage = errorMessage;
        }
    }

    private static class GroupByFieldsResult {
        final boolean success;
        final List<SelectField> fields;
        final String errorMessage;

        GroupByFieldsResult(List<SelectField> fields) {
            this.success = true;
            this.fields = fields;
            this.errorMessage = null;
        }

        GroupByFieldsResult(String errorMessage) {
            this.success = false;
            this.fields = Collections.emptyList();
            this.errorMessage = errorMessage;
        }
    }

    private static class HavingConditionsResult {
        final boolean success;
        final List<HavingCondition> conditions;
        final String errorMessage;

        HavingConditionsResult(List<HavingCondition> conditions) {
            this.success = true;
            this.conditions = conditions;
            this.errorMessage = null;
        }

        HavingConditionsResult(String errorMessage) {
            this.success = false;
            this.conditions = Collections.emptyList();
            this.errorMessage = errorMessage;
        }
    }

    private static SelectFieldsResult extractSelectFields(String json, String primaryTable, SchemaMetadata schema) {
        List<SelectField> fields = new ArrayList<>();
        Pattern pArray = Pattern.compile("\"(?:selectFields|select|columns)\"\\s*:\\s*\\[([^\\]]*)\\]", Pattern.CASE_INSENSITIVE);
        Matcher mArray = pArray.matcher(json);

        if (mArray.find()) {
            String arrayContent = mArray.group(1);
            Pattern pObj = Pattern.compile("\\{([^\\}]+)\\}");
            Matcher mObj = pObj.matcher(arrayContent);

            while (mObj.find()) {
                String obj = mObj.group(1);
                String table = extractJsonString(obj, "tableName", "table");
                if (table == null) table = primaryTable;

                String col = extractJsonString(obj, "columnName", "column", "name");
                if (col == null) col = "*";

                String alias = extractJsonString(obj, "alias");
                String aggStr = extractJsonString(obj, "aggregationType", "aggregation");

                AggregationType agg = AggregationType.NONE;
                if (aggStr != null) {
                    try {
                        agg = AggregationType.valueOf(aggStr.toUpperCase());
                    } catch (Exception ignored) {}
                }

                if (!schema.hasTable(table)) {
                    return new SelectFieldsResult("Hallucination Defense: Referenced nonexistent table '" + table + "' in select fields");
                }

                if (!col.equals("*") && !schema.hasColumn(table, col)) {
                    return new SelectFieldsResult("Hallucination Defense: Referenced nonexistent select column '" + col + "' on table '" + table + "'");
                }

                fields.add(new SelectField(table, col, alias, agg));
            }
        }

        if (fields.isEmpty()) {
            fields.add(new SelectField(primaryTable, "*"));
        }
        return new SelectFieldsResult(fields);
    }

    private static FilterConditionsResult extractFilterConditions(String json, String primaryTable, SchemaMetadata schema) {
        List<FilterCondition> filters = new ArrayList<>();
        Pattern pArray = Pattern.compile("\"(?:filterConditions|filters|filter)\"\\s*:\\s*\\[([^\\]]*)\\]", Pattern.CASE_INSENSITIVE);
        Matcher mArray = pArray.matcher(json);

        if (mArray.find()) {
            String arrayContent = mArray.group(1);
            Pattern pObj = Pattern.compile("\\{([^\\}]+)\\}");
            Matcher mObj = pObj.matcher(arrayContent);

            while (mObj.find()) {
                String obj = mObj.group(1);
                String table = extractJsonString(obj, "tableName", "table");
                if (table == null) table = primaryTable;

                String col = extractJsonString(obj, "columnName", "column", "field");
                String opStr = extractJsonString(obj, "operator", "op");
                Object val = extractJsonValue(obj, "value");

                if (!schema.hasTable(table)) {
                    return new FilterConditionsResult("Hallucination Defense: Referenced nonexistent table '" + table + "' in filter");
                }

                if (col == null || !schema.hasColumn(table, col)) {
                    return new FilterConditionsResult("Hallucination Defense: Referenced nonexistent filter column '" + col + "' on table '" + table + "'");
                }

                if (opStr == null || opStr.trim().isEmpty()) {
                    return new FilterConditionsResult("Validation Error: Missing operator in filter condition on column '" + col + "'");
                }

                Operator op = parseOperator(opStr);
                if (op == null) {
                    return new FilterConditionsResult("Validation Error: Invalid or unsupported operator '" + opStr + "' in filter condition");
                }

                filters.add(new FilterCondition(table, col, op, val));
            }
        }
        return new FilterConditionsResult(filters);
    }

    private static JoinConditionsResult extractJoinConditions(String json, SchemaMetadata schema) {
        List<JoinCondition> joins = new ArrayList<>();
        Pattern pArray = Pattern.compile("\"(?:joinConditions|joins|join)\"\\s*:\\s*\\[([^\\]]*)\\]", Pattern.CASE_INSENSITIVE);
        Matcher mArray = pArray.matcher(json);

        if (mArray.find()) {
            String arrayContent = mArray.group(1);
            Pattern pObj = Pattern.compile("\\{([^\\}]+)\\}");
            Matcher mObj = pObj.matcher(arrayContent);

            while (mObj.find()) {
                String obj = mObj.group(1);
                String leftTbl = extractJsonString(obj, "leftTable", "table1", "sourceTable");
                String leftCol = extractJsonString(obj, "leftColumn", "column1", "sourceColumn");
                String rightTbl = extractJsonString(obj, "rightTable", "table2", "targetTable");
                String rightCol = extractJsonString(obj, "rightColumn", "column2", "targetColumn");
                String typeStr = extractJsonString(obj, "type", "joinType");

                if (leftTbl == null || rightTbl == null || leftCol == null || rightCol == null) {
                    return new JoinConditionsResult("Validation Error: Join condition missing required fields (leftTable, leftColumn, rightTable, rightColumn)");
                }

                if (!schema.hasTable(leftTbl)) {
                    return new JoinConditionsResult("Hallucination Defense: Referenced nonexistent table '" + leftTbl + "' in join condition");
                }
                if (!schema.hasTable(rightTbl)) {
                    return new JoinConditionsResult("Hallucination Defense: Referenced nonexistent table '" + rightTbl + "' in join condition");
                }
                if (!schema.hasColumn(leftTbl, leftCol)) {
                    return new JoinConditionsResult("Hallucination Defense: Referenced nonexistent column '" + leftCol + "' on table '" + leftTbl + "' in join condition");
                }
                if (!schema.hasColumn(rightTbl, rightCol)) {
                    return new JoinConditionsResult("Hallucination Defense: Referenced nonexistent column '" + rightCol + "' on table '" + rightTbl + "' in join condition");
                }

                if (!isValidJoinRelationship(schema, leftTbl, leftCol, rightTbl, rightCol)) {
                    return new JoinConditionsResult("Validation Error: Invalid join relationship between '" + leftTbl + "." + leftCol + "' and '" + rightTbl + "." + rightCol + "' (no matching foreign key or relation in schema)");
                }

                JoinType jType = JoinType.INNER;
                if (typeStr != null) {
                    try {
                        jType = JoinType.valueOf(typeStr.toUpperCase());
                    } catch (Exception ignored) {}
                }

                joins.add(new JoinCondition(leftTbl, leftCol, rightTbl, rightCol, jType));
            }
        }
        return new JoinConditionsResult(joins);
    }

    private static boolean isValidJoinRelationship(SchemaMetadata schema, String leftTbl, String leftCol, String rightTbl, String rightCol) {
        // Direct Foreign Key check
        for (ForeignKeyMetadata fk : schema.getRelationships(leftTbl)) {
            if (fk.getFkColumnName().equalsIgnoreCase(leftCol) &&
                fk.getPkTableName().equalsIgnoreCase(rightTbl) &&
                fk.getPkColumnName().equalsIgnoreCase(rightCol)) {
                return true;
            }
        }
        for (ForeignKeyMetadata fk : schema.getRelationships(rightTbl)) {
            if (fk.getFkColumnName().equalsIgnoreCase(rightCol) &&
                fk.getPkTableName().equalsIgnoreCase(leftTbl) &&
                fk.getPkColumnName().equalsIgnoreCase(leftCol)) {
                return true;
            }
        }

        // Schema path graph check
        SchemaPathResolver.JoinPathResult pathResult = SchemaPathResolver.resolveJoinPath(schema, List.of(leftTbl, rightTbl));
        if (pathResult.getStatus() == SchemaPathResolver.JoinPathResult.PathStatus.SUCCESS) {
            for (JoinCondition jc : pathResult.getJoinConditions()) {
                if ((jc.getLeftTable().equalsIgnoreCase(leftTbl) && jc.getLeftColumn().equalsIgnoreCase(leftCol) &&
                     jc.getRightTable().equalsIgnoreCase(rightTbl) && jc.getRightColumn().equalsIgnoreCase(rightCol)) ||
                    (jc.getLeftTable().equalsIgnoreCase(rightTbl) && jc.getLeftColumn().equalsIgnoreCase(rightCol) &&
                     jc.getRightTable().equalsIgnoreCase(leftTbl) && jc.getRightColumn().equalsIgnoreCase(leftCol))) {
                    return true;
                }
            }
        }

        // Same column name join between related tables
        if (leftCol.equalsIgnoreCase(rightCol)) {
            if (pathResult.getStatus() == SchemaPathResolver.JoinPathResult.PathStatus.SUCCESS) {
                return true;
            }
        }

        return false;
    }

    private static OrderSpecificationsResult extractOrderSpecifications(String json, String primaryTable, SchemaMetadata schema) {
        List<OrderSpecification> orders = new ArrayList<>();
        Pattern pArray = Pattern.compile("\"(?:orderSpecifications|orderBy|orders|order)\"\\s*:\\s*\\[([^\\]]*)\\]", Pattern.CASE_INSENSITIVE);
        Matcher mArray = pArray.matcher(json);

        if (mArray.find()) {
            String arrayContent = mArray.group(1);
            Pattern pObj = Pattern.compile("\\{([^\\}]+)\\}");
            Matcher mObj = pObj.matcher(arrayContent);

            while (mObj.find()) {
                String obj = mObj.group(1);
                String table = extractJsonString(obj, "tableName", "table");
                if (table == null) table = primaryTable;

                String col = extractJsonString(obj, "columnName", "column");
                Boolean asc = extractBoolean(obj, "ascending");

                if (!schema.hasTable(table)) {
                    return new OrderSpecificationsResult("Hallucination Defense: Referenced nonexistent table '" + table + "' in order-by");
                }

                if (col == null || !schema.hasColumn(table, col)) {
                    return new OrderSpecificationsResult("Hallucination Defense: Referenced nonexistent order-by column '" + col + "' on table '" + table + "'");
                }

                orders.add(new OrderSpecification(table, col, asc != null ? asc : true));
            }
        }
        return new OrderSpecificationsResult(orders);
    }

    private static GroupByFieldsResult extractGroupByFields(String json, String primaryTable, SchemaMetadata schema) {
        List<SelectField> fields = new ArrayList<>();
        Pattern pArray = Pattern.compile("\"(?:groupByFields|groupBy|group_by|group)\"\\s*:\\s*\\[([^\\]]*)\\]", Pattern.CASE_INSENSITIVE);
        Matcher mArray = pArray.matcher(json);

        if (mArray.find()) {
            String arrayContent = mArray.group(1);
            Pattern pObj = Pattern.compile("\\{([^\\}]+)\\}");
            Matcher mObj = pObj.matcher(arrayContent);

            boolean foundObject = false;
            while (mObj.find()) {
                foundObject = true;
                String obj = mObj.group(1);
                String table = extractJsonString(obj, "tableName", "table");
                if (table == null) table = primaryTable;

                String col = extractJsonString(obj, "columnName", "column", "name");
                if (col == null) {
                    return new GroupByFieldsResult("Malformed group-by field: missing column");
                }

                if (!schema.hasTable(table)) {
                    return new GroupByFieldsResult("Hallucination Defense: Referenced nonexistent table '" + table + "' in group-by");
                }

                if (!schema.hasColumn(table, col)) {
                    return new GroupByFieldsResult("Hallucination Defense: Referenced nonexistent group-by column '" + col + "' on table '" + table + "'");
                }

                fields.add(new SelectField(table, col));
            }

            if (!foundObject) {
                Pattern pStr = Pattern.compile("\"([^\"]+)\"");
                Matcher mStr = pStr.matcher(arrayContent);
                while (mStr.find()) {
                    String col = mStr.group(1);
                    String table = primaryTable;
                    if (col.contains(".")) {
                        String[] parts = col.split("\\.", 2);
                        table = parts[0];
                        col = parts[1];
                    }
                    if (!schema.hasTable(table)) {
                        return new GroupByFieldsResult("Hallucination Defense: Referenced nonexistent table '" + table + "' in group-by");
                    }
                    if (!schema.hasColumn(table, col)) {
                        return new GroupByFieldsResult("Hallucination Defense: Referenced nonexistent group-by column '" + col + "' on table '" + table + "'");
                    }
                    fields.add(new SelectField(table, col));
                }
            }
        }
        return new GroupByFieldsResult(fields);
    }

    private static HavingConditionsResult extractHavingConditions(String json, String primaryTable, SchemaMetadata schema) {
        List<HavingCondition> conditions = new ArrayList<>();
        Pattern pArray = Pattern.compile("\"(?:havingConditions|having|having_conditions)\"\\s*:\\s*\\[([^\\]]*)\\]", Pattern.CASE_INSENSITIVE);
        Matcher mArray = pArray.matcher(json);

        if (mArray.find()) {
            String arrayContent = mArray.group(1);
            Pattern pObj = Pattern.compile("\\{([^\\}]+)\\}");
            Matcher mObj = pObj.matcher(arrayContent);

            while (mObj.find()) {
                String obj = mObj.group(1);
                String table = extractJsonString(obj, "tableName", "table");
                if (table == null) table = primaryTable;

                String col = extractJsonString(obj, "columnName", "column");
                if (col == null) col = "*";

                String aggStr = extractJsonString(obj, "aggregationType", "aggregation");
                AggregationType agg = AggregationType.COUNT;
                if (aggStr != null) {
                    try {
                        agg = AggregationType.valueOf(aggStr.toUpperCase());
                    } catch (Exception ignored) {}
                }

                String opStr = extractJsonString(obj, "operator", "op");
                Operator op = parseOperator(opStr);
                if (op == null) op = Operator.GREATER_THAN;

                Object val = extractJsonValue(obj, "value");

                if (!schema.hasTable(table)) {
                    return new HavingConditionsResult("Hallucination Defense: Referenced nonexistent table '" + table + "' in having condition");
                }

                if (!col.equals("*") && !schema.hasColumn(table, col)) {
                    return new HavingConditionsResult("Hallucination Defense: Referenced nonexistent having column '" + col + "' on table '" + table + "'");
                }

                conditions.add(new HavingCondition(table, col, agg, op, val));
            }
        }
        return new HavingConditionsResult(conditions);
    }

    private static String extractJsonString(String json, String... keys) {
        for (String key : keys) {
            Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static Object extractJsonValue(String json, String key) {
        Pattern pStr = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
        Matcher mStr = pStr.matcher(json);
        if (mStr.find()) {
            return mStr.group(1);
        }
        Pattern pNum = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher mNum = pNum.matcher(json);
        if (mNum.find()) {
            String numStr = mNum.group(1);
            if (numStr.contains(".")) {
                return Double.parseDouble(numStr);
            } else {
                return Long.parseLong(numStr);
            }
        }
        Pattern pBool = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
        Matcher mBool = pBool.matcher(json);
        if (mBool.find()) {
            return Boolean.parseBoolean(mBool.group(1));
        }
        return null;
    }

    public static Operator parseOperator(String opStr) {
        if (opStr == null || opStr.trim().isEmpty()) return null;
        String trimmed = opStr.trim();
        switch (trimmed) {
            case "=":
            case "==":
                return Operator.EQUALS;
            case "!=":
            case "<>":
                return Operator.NOT_EQUALS;
            case ">":
                return Operator.GREATER_THAN;
            case "<":
                return Operator.LESS_THAN;
            case ">=":
                return Operator.GREATER_THAN_OR_EQUAL;
            case "<=":
                return Operator.LESS_THAN_OR_EQUAL;
            case "LIKE":
            case "like":
                return Operator.LIKE;
            case "IN":
            case "in":
                return Operator.IN;
            default:
                try {
                    return Operator.valueOf(trimmed.toUpperCase());
                } catch (Exception e) {
                    return null; // Unknown/invalid operator -> rejected
                }
        }
    }
}
