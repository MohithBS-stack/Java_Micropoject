package com.nl2sql.generator;

import com.nl2sql.metadata.ColumnMetadata;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.metadata.TableMetadata;
import com.nl2sql.nlp.Intent;
import com.nl2sql.plan.*;

import java.util.*;

/**
 * Generic, domain-independent SQL Generator that transforms a QueryPlan into a safe,
 * parameterized GeneratedQuery based on dynamic SchemaMetadata.
 */
public class GenericQueryPlanSQLGenerator implements QueryPlanSQLGenerator {

    @Override
    public GeneratedQuery generate(QueryPlan plan, SchemaMetadata schema) {
        if (plan == null) {
            return new GeneratedQuery(Intent.UNKNOWN, "QueryPlan is null");
        }

        // 1. Refuse generation if plan status is AMBIGUOUS or UNSUPPORTED
        if (plan.getStatus() == QueryPlanStatus.AMBIGUOUS) {
            return new GeneratedQuery(Intent.UNKNOWN, "Query generation refused: QueryPlan is AMBIGUOUS. " + plan.getStatusReason());
        }
        if (plan.getStatus() == QueryPlanStatus.UNSUPPORTED) {
            return new GeneratedQuery(Intent.UNKNOWN, "Query generation refused: QueryPlan is UNSUPPORTED. " + plan.getStatusReason());
        }
        if (schema == null) {
            return new GeneratedQuery(Intent.UNKNOWN, "SchemaMetadata is null");
        }

        // 2. Validate target tables in QueryPlan against SchemaMetadata
        List<String> targetTables = plan.getTargetTables();
        if (targetTables.isEmpty()) {
            return new GeneratedQuery(Intent.UNKNOWN, "QueryPlan contains no target tables");
        }

        for (String tbl : targetTables) {
            if (!schema.hasTable(tbl)) {
                return new GeneratedQuery(Intent.UNKNOWN, "Schema Validation Error: Table '" + tbl + "' does not exist in discovered schema");
            }
        }

        Map<String, String> tableAliases = generateTableAliases(targetTables);

        // Validate select fields against schema
        for (SelectField sf : plan.getSelectFields()) {
            if (!sf.getColumnName().equals("*")) {
                String tbl = sf.getTableName() != null ? sf.getTableName() : targetTables.get(0);
                if (!schema.hasColumn(tbl, sf.getColumnName())) {
                    return new GeneratedQuery(Intent.UNKNOWN, "Schema Validation Error: Column '" + sf.getColumnName() + "' does not exist in table '" + tbl + "'");
                }
            }
        }

        // Validate filter conditions against schema
        for (FilterCondition fc : plan.getFilterConditions()) {
            String tbl = fc.getTableName() != null ? fc.getTableName() : targetTables.get(0);
            if (!schema.hasColumn(tbl, fc.getColumnName())) {
                return new GeneratedQuery(Intent.UNKNOWN, "Schema Validation Error: Column '" + fc.getColumnName() + "' does not exist in table '" + tbl + "'");
            }
        }

        // Validate join conditions against schema
        for (JoinCondition jc : plan.getJoinConditions()) {
            if (!schema.hasTable(jc.getLeftTable()) || !schema.hasColumn(jc.getLeftTable(), jc.getLeftColumn())) {
                return new GeneratedQuery(Intent.UNKNOWN, "Schema Validation Error: Invalid join left side " + jc.getLeftTable() + "." + jc.getLeftColumn());
            }
            if (!schema.hasTable(jc.getRightTable()) || !schema.hasColumn(jc.getRightTable(), jc.getRightColumn())) {
                return new GeneratedQuery(Intent.UNKNOWN, "Schema Validation Error: Invalid join right side " + jc.getRightTable() + "." + jc.getRightColumn());
            }
        }

        // 3. Build SQL String and Parameter List
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        // SELECT clause
        sql.append("SELECT ");
        if (plan.isDistinct()) {
            sql.append("DISTINCT ");
        }

        List<SelectField> selectFields = plan.getSelectFields();
        List<String> selectParts = new ArrayList<>();

        for (SelectField sf : selectFields) {
            String tbl = sf.getTableName() != null ? sf.getTableName() : targetTables.get(0);
            String alias = tableAliases.get(tbl.toLowerCase());
            String colName = sf.getColumnName();

            String fieldSql;
            if (colName.equals("*")) {
                if (sf.getAggregationType() == AggregationType.COUNT) {
                    fieldSql = "COUNT(*)";
                } else {
                    fieldSql = alias + ".*";
                }
            } else {
                if (sf.getAggregationType() != AggregationType.NONE) {
                    fieldSql = sf.getAggregationType().name() + "(" + alias + "." + colName + ")";
                } else {
                    fieldSql = alias + "." + colName;
                }
            }

            if (sf.getAlias() != null && !sf.getAlias().trim().isEmpty()) {
                fieldSql += " AS " + sf.getAlias();
            }
            selectParts.add(fieldSql);
        }

        sql.append(String.join(", ", selectParts));

        // FROM clause
        String primaryTable = targetTables.get(0);
        String primaryAlias = tableAliases.get(primaryTable.toLowerCase());
        sql.append(" FROM ").append(primaryTable).append(" ").append(primaryAlias);

        // JOIN clause
        Set<String> joinedTables = new HashSet<>();
        joinedTables.add(primaryTable.toLowerCase());

        for (JoinCondition jc : plan.getJoinConditions()) {
            String leftTbl = jc.getLeftTable().toLowerCase();
            String rightTbl = jc.getRightTable().toLowerCase();

            String newTable;
            String existingTable;
            String newCol;
            String existingCol;

            if (joinedTables.contains(leftTbl) && !joinedTables.contains(rightTbl)) {
                existingTable = leftTbl;
                existingCol = jc.getLeftColumn();
                newTable = rightTbl;
                newCol = jc.getRightColumn();
            } else if (joinedTables.contains(rightTbl) && !joinedTables.contains(leftTbl)) {
                existingTable = rightTbl;
                existingCol = jc.getRightColumn();
                newTable = leftTbl;
                newCol = jc.getLeftColumn();
            } else {
                existingTable = leftTbl;
                existingCol = jc.getLeftColumn();
                newTable = rightTbl;
                newCol = jc.getRightColumn();
            }

            String newAlias = tableAliases.get(newTable);
            String existingAlias = tableAliases.get(existingTable);

            sql.append(" ").append(jc.getJoinType().name()).append(" JOIN ")
               .append(schema.getTable(newTable).getTableName()).append(" ").append(newAlias)
               .append(" ON ").append(existingAlias).append(".").append(existingCol)
               .append(" = ").append(newAlias).append(".").append(newCol);

            joinedTables.add(newTable);
        }

        // WHERE clause
        List<FilterCondition> filters = plan.getFilterConditions();
        if (!filters.isEmpty()) {
            sql.append(" WHERE ");
            List<String> filterParts = new ArrayList<>();
            for (FilterCondition fc : filters) {
                String tbl = fc.getTableName() != null ? fc.getTableName() : primaryTable;
                String alias = tableAliases.get(tbl.toLowerCase());
                
                filterParts.add(alias + "." + fc.getColumnName() + " " + fc.getOperator().getSymbol() + " ?");
                parameters.add(fc.getValue());
            }
            sql.append(String.join(" AND ", filterParts));
        }

        // GROUP BY clause
        List<SelectField> groupBys = plan.getGroupByFields();
        if (!groupBys.isEmpty()) {
            sql.append(" GROUP BY ");
            List<String> groupParts = new ArrayList<>();
            for (SelectField gf : groupBys) {
                String tbl = gf.getTableName() != null ? gf.getTableName() : primaryTable;
                String alias = tableAliases.get(tbl.toLowerCase());
                groupParts.add(alias + "." + gf.getColumnName());
            }
            sql.append(String.join(", ", groupParts));
        }

        // HAVING clause
        List<HavingCondition> havings = plan.getHavingConditions();
        if (!havings.isEmpty()) {
            sql.append(" HAVING ");
            List<String> havingParts = new ArrayList<>();
            for (HavingCondition hc : havings) {
                String tbl = hc.getTableName() != null ? hc.getTableName() : primaryTable;
                String alias = tableAliases.get(tbl.toLowerCase());
                String colRef = hc.getColumnName().equals("*") ? "*" : (alias + "." + hc.getColumnName());
                havingParts.add(hc.getAggregationType().name() + "(" + colRef + ") " + hc.getOperator().getSymbol() + " ?");
                parameters.add(hc.getValue());
            }
            sql.append(String.join(" AND ", havingParts));
        }

        // ORDER BY clause
        List<OrderSpecification> orderBys = plan.getOrderSpecifications();
        if (!orderBys.isEmpty()) {
            sql.append(" ORDER BY ");
            List<String> orderParts = new ArrayList<>();
            for (OrderSpecification os : orderBys) {
                String tbl = os.getTableName() != null ? os.getTableName() : primaryTable;
                String alias = tableAliases.get(tbl.toLowerCase());
                orderParts.add(alias + "." + os.getColumnName() + " " + (os.isAscending() ? "ASC" : "DESC"));
            }
            sql.append(String.join(", ", orderParts));
        }

        // LIMIT clause
        if (plan.getLimit() != null) {
            sql.append(" LIMIT ?");
            parameters.add(plan.getLimit());
        }

        Intent nlpIntent = plan.getIntent() == QueryIntent.COUNT ? Intent.COUNT_RECORDS : Intent.FIND_TEST;
        return new GeneratedQuery(sql.toString(), parameters, targetTables, nlpIntent);
    }

    private Map<String, String> generateTableAliases(List<String> targetTables) {
        Map<String, String> aliases = new HashMap<>();
        Set<String> used = new HashSet<>();

        for (String table : targetTables) {
            String lower = table.toLowerCase();
            String candidate;
            if (lower.contains("_")) {
                StringBuilder sb = new StringBuilder();
                for (String part : lower.split("_")) {
                    if (!part.isEmpty()) sb.append(part.charAt(0));
                }
                candidate = sb.toString();
            } else {
                candidate = String.valueOf(lower.charAt(0));
            }

            if (used.contains(candidate)) {
                int count = 1;
                while (used.contains(candidate + count)) {
                    count++;
                }
                candidate = candidate + count;
            }

            used.add(candidate);
            aliases.put(lower, candidate);
        }
        return aliases;
    }
}
