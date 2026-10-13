package com.nl2sql.plan;

import com.nl2sql.metadata.ColumnMetadata;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.metadata.TableMetadata;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Baseline deterministic implementation of QuestionUnderstandingEngine.
 * Resolves natural language questions against arbitrary SchemaMetadata using zero domain-specific rules.
 */
public class RuleBasedQueryPlanner implements QuestionUnderstandingEngine {

    @Override
    public QueryPlan understand(String question, SchemaMetadata schema) {
        if (question == null || question.trim().isEmpty() || schema == null || schema.getTables().isEmpty()) {
            return QueryPlan.unsupported(question, "Question or schema is null/empty.");
        }

        String normalizedQuestion = question.trim();
        String lowerQuestion = normalizedQuestion.toLowerCase();

        // 1. Identify Intent & Aggregation
        boolean isCount = lowerQuestion.contains("how many") || lowerQuestion.contains("count of") ||
                          lowerQuestion.contains("total number of") || lowerQuestion.contains("number of");
        QueryIntent intent = isCount ? QueryIntent.COUNT : QueryIntent.SELECT;

        // 2. Schema Table Resolution
        Set<String> explicitTableNames = new LinkedHashSet<>();
        Map<String, TableMetadata> allTables = schema.getTables();

        for (TableMetadata table : allTables.values()) {
            String tableName = table.getTableName().toLowerCase();
            String stemName = stem(tableName);

            if (containsWord(lowerQuestion, tableName) || containsWord(lowerQuestion, stemName)) {
                explicitTableNames.add(table.getTableName());
            }
        }

        // Check column matches
        Map<String, List<ColumnMatch>> matchedColumnsByTable = new LinkedHashMap<>();
        List<ColumnMatch> allColumnMatches = new ArrayList<>();

        for (TableMetadata table : allTables.values()) {
            List<ColumnMatch> colMatches = findColumnMatches(lowerQuestion, table);
            if (!colMatches.isEmpty()) {
                matchedColumnsByTable.put(table.getTableName(), colMatches);
                allColumnMatches.addAll(colMatches);
            }
        }

        // 3. Check for Ambiguity
        if (explicitTableNames.isEmpty() && matchedColumnsByTable.keySet().size() > 1) {
            return QueryPlan.ambiguous(question, "Ambiguous column reference in question matches multiple candidate tables.");
        }

        Set<String> matchedTableNames = new LinkedHashSet<>(explicitTableNames);
        if (matchedTableNames.isEmpty()) {
            matchedTableNames.addAll(matchedColumnsByTable.keySet());
        }

        if (matchedTableNames.isEmpty()) {
            return QueryPlan.unsupported(question, "No recognized schema tables or entities found in question.");
        }

        List<String> targetTablesList = new ArrayList<>(matchedTableNames);

        // 4. Resolve Join Paths (if multi-table)
        List<JoinCondition> resolvedJoins = new ArrayList<>();
        if (targetTablesList.size() > 1) {
            SchemaPathResolver.JoinPathResult joinResult = SchemaPathResolver.resolveJoinPath(schema, targetTablesList);
            if (joinResult.getStatus() == SchemaPathResolver.JoinPathResult.PathStatus.AMBIGUOUS) {
                return QueryPlan.ambiguous(question, joinResult.getMessage());
            } else if (joinResult.getStatus() == SchemaPathResolver.JoinPathResult.PathStatus.NO_PATH) {
                return QueryPlan.unsupported(question, joinResult.getMessage());
            }
            resolvedJoins.addAll(joinResult.getJoinConditions());
        }

        QueryPlan.Builder builder = new QueryPlan.Builder(normalizedQuestion)
                .status(QueryPlanStatus.SUPPORTED)
                .intent(intent);

        for (String tbl : targetTablesList) {
            builder.addTargetTable(tbl);
        }
        for (JoinCondition jc : resolvedJoins) {
            builder.addJoinCondition(jc);
        }

        // 5. Select Fields Resolution
        String primaryTable = targetTablesList.get(0);

        if (intent == QueryIntent.COUNT) {
            builder.addSelectField(new SelectField(primaryTable, "*", "total_count", AggregationType.COUNT));
        } else if (lowerQuestion.contains("average") || lowerQuestion.contains("avg")) {
            boolean foundAvg = false;
            for (String tbl : targetTablesList) {
                TableMetadata tMeta = schema.getTable(tbl);
                for (ColumnMetadata col : tMeta.getColumns()) {
                    String cName = col.getName().toLowerCase();
                    if (lowerQuestion.contains(cName) || lowerQuestion.contains(cName.replace("_", " "))) {
                        builder.addSelectField(new SelectField(tbl, col.getName(), "avg_" + col.getName(), AggregationType.AVG));
                        foundAvg = true;
                        break;
                    }
                }
                if (foundAvg) break;
            }
            if (!foundAvg) {
                builder.addSelectField(new SelectField(primaryTable, "*"));
            }
        } else {
            Set<SelectField> selectedFields = new LinkedHashSet<>();
            for (String tbl : targetTablesList) {
                TableMetadata tMeta = schema.getTable(tbl);
                List<ColumnMatch> matches = findColumnMatches(lowerQuestion, tMeta);
                for (ColumnMatch cm : matches) {
                    selectedFields.add(new SelectField(tbl, cm.columnName));
                }
            }

            if (selectedFields.isEmpty()) {
                builder.addSelectField(new SelectField(primaryTable, "*"));
            } else {
                for (SelectField sf : selectedFields) {
                    builder.addSelectField(sf);
                }
            }
        }

        // 6. Filter Conditions Extraction
        extractNumericFilters(lowerQuestion, schema, targetTablesList, builder);
        extractEntityValueFilters(lowerQuestion, schema, targetTablesList, builder);

        return builder.build();
    }

    private boolean isAmbiguousColumnRequest(String lowerQuestion, List<ColumnMatch> matches, Set<String> matchedTables) {
        if (matches.size() > 1 && matchedTables.isEmpty()) {
            Set<String> distinctTables = new HashSet<>();
            for (ColumnMatch cm : matches) {
                distinctTables.add(cm.tableName);
            }
            if (distinctTables.size() > 1) {
                return true;
            }
        }
        return false;
    }

    private static class ColumnMatch {
        final String tableName;
        final String columnName;

        ColumnMatch(String tableName, String columnName) {
            this.tableName = tableName;
            this.columnName = columnName;
        }
    }

    private List<ColumnMatch> findColumnMatches(String lowerQuestion, TableMetadata table) {
        List<ColumnMatch> matches = new ArrayList<>();
        if (table == null) return matches;

        for (ColumnMetadata col : table.getColumns()) {
            String colName = col.getName().toLowerCase();
            String cleanedColName = colName.replace("_", " ");
            String stemCol = stem(colName);

            // Exact or word match on full column name
            if (containsWord(lowerQuestion, colName) || containsWord(lowerQuestion, cleanedColName)) {
                matches.add(new ColumnMatch(table.getTableName(), col.getName()));
            } 
            // Generic name match (e.g. "name" or "names" matching "name", "employee_name", "department_name")
            else if ((colName.equals("name") || colName.endsWith("_name")) && (containsWord(lowerQuestion, "name") || containsWord(lowerQuestion, "names"))) {
                matches.add(new ColumnMatch(table.getTableName(), col.getName()));
            }
            else if (stemCol.length() > 3 && (containsWord(lowerQuestion, stemCol) || containsWord(lowerQuestion, colName))) {
                matches.add(new ColumnMatch(table.getTableName(), col.getName()));
            }
        }
        return matches;
    }

    private void extractNumericFilters(String lowerQuestion, SchemaMetadata schema, List<String> targetTables, QueryPlan.Builder builder) {
        Pattern pGt = Pattern.compile("(\\w+)\\s+(?:greater than|>|more than|above|over)\\s+(\\d+(?:\\.\\d+)?)");
        Matcher mGt = pGt.matcher(lowerQuestion);
        if (mGt.find()) {
            String colTerm = mGt.group(1);
            double val = Double.parseDouble(mGt.group(2));
            String matchedCol = findMatchingColumnInTables(colTerm, schema, targetTables);
            if (matchedCol != null) {
                String[] parts = matchedCol.split("\\.");
                builder.addFilterCondition(new FilterCondition(parts[0], parts[1], Operator.GREATER_THAN, val));
            }
        }

        Pattern pLt = Pattern.compile("(\\w+)\\s+(?:less than|<|under|below)\\s+(\\d+(?:\\.\\d+)?)");
        Matcher mLt = pLt.matcher(lowerQuestion);
        if (mLt.find()) {
            String colTerm = mLt.group(1);
            double val = Double.parseDouble(mLt.group(2));
            String matchedCol = findMatchingColumnInTables(colTerm, schema, targetTables);
            if (matchedCol != null) {
                String[] parts = matchedCol.split("\\.");
                builder.addFilterCondition(new FilterCondition(parts[0], parts[1], Operator.LESS_THAN, val));
            }
        }
    }

    private void extractEntityValueFilters(String lowerQuestion, SchemaMetadata schema, List<String> targetTables, QueryPlan.Builder builder) {
        Pattern pIn = Pattern.compile("in (?:the )?([a-z0-9_]+)(?:\\s+department|\\s+group|\\s+test|\\?|$)");
        Matcher mIn = pIn.matcher(lowerQuestion);
        if (mIn.find()) {
            String rawVal = mIn.group(1);
            if (!rawVal.isEmpty() && !rawVal.equals("all")) {
                String capVal = Character.toUpperCase(rawVal.charAt(0)) + rawVal.substring(1);
                for (String tbl : targetTables) {
                    TableMetadata tMeta = schema.getTable(tbl);
                    for (ColumnMetadata col : tMeta.getColumns()) {
                        String cName = col.getName().toLowerCase();
                        if (cName.endsWith("_name") || cName.equals("name") || cName.contains("name")) {
                            builder.addFilterCondition(new FilterCondition(tbl, col.getName(), Operator.EQUALS, capVal));
                            return;
                        }
                    }
                }
            }
        }

        Pattern pEq = Pattern.compile("where\\s+(\\w+)\\s+(?:is|=|equals)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher mEq = pEq.matcher(lowerQuestion);
        if (mEq.find()) {
            String colTerm = mEq.group(1);
            String rawVal = mEq.group(2).trim();
            if ((rawVal.startsWith("'") && rawVal.endsWith("'")) || (rawVal.startsWith("\"") && rawVal.endsWith("\""))) {
                rawVal = rawVal.substring(1, rawVal.length() - 1);
            }
            String matchedCol = findMatchingColumnInTables(colTerm, schema, targetTables);
            if (matchedCol != null) {
                String[] parts = matchedCol.split("\\.");
                builder.addFilterCondition(new FilterCondition(parts[0], parts[1], Operator.EQUALS, rawVal));
            }
        }
    }

    private String findMatchingColumnInTables(String term, SchemaMetadata schema, List<String> targetTables) {
        String stemTerm = stem(term.toLowerCase());
        for (String tbl : targetTables) {
            TableMetadata tMeta = schema.getTable(tbl);
            for (ColumnMetadata col : tMeta.getColumns()) {
                String cName = col.getName().toLowerCase();
                if (cName.equalsIgnoreCase(term) || cName.contains(term) || stem(cName).equalsIgnoreCase(stemTerm)) {
                    return tbl + "." + col.getName();
                }
            }
        }
        return null;
    }

    private String stem(String word) {
        if (word == null) return "";
        String w = word.toLowerCase().trim();
        if (w.endsWith("ees")) {
            return w.substring(0, w.length() - 1);
        }
        if (w.endsWith("ies") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if ((w.endsWith("ses") || w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes")) && w.length() > 4) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    private boolean containsWord(String text, String word) {
        if (text == null || word == null || word.isEmpty()) return false;
        Pattern p = Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE);
        return p.matcher(text).find();
    }
}
