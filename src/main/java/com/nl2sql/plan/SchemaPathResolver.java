package com.nl2sql.plan;

import com.nl2sql.metadata.ForeignKeyMetadata;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.metadata.TableMetadata;

import java.util.*;

/**
 * Discovers join paths between target tables dynamically using Foreign Key relationships
 * in SchemaMetadata. Supports single-hop and multi-hop relationships.
 */
public class SchemaPathResolver {

    public static class Edge {
        private final String sourceTable;
        private final String sourceColumn;
        private final String targetTable;
        private final String targetColumn;

        public Edge(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
            this.sourceTable = sourceTable;
            this.sourceColumn = sourceColumn;
            this.targetTable = targetTable;
            this.targetColumn = targetColumn;
        }

        public String getSourceTable() { return sourceTable; }
        public String getSourceColumn() { return sourceColumn; }
        public String getTargetTable() { return targetTable; }
        public String getTargetColumn() { return targetColumn; }
    }

    public static class JoinPathResult {
        public enum PathStatus { SUCCESS, NO_PATH, AMBIGUOUS }

        private final PathStatus status;
        private final List<JoinCondition> joinConditions;
        private final String message;

        public JoinPathResult(PathStatus status, List<JoinCondition> joinConditions, String message) {
            this.status = status;
            this.joinConditions = joinConditions != null ? joinConditions : Collections.emptyList();
            this.message = message;
        }

        public PathStatus getStatus() { return status; }
        public List<JoinCondition> getJoinConditions() { return joinConditions; }
        public String getMessage() { return message; }
    }

    /**
     * Finds join conditions to connect a list of target tables using foreign key metadata discovered in schema.
     *
     * @param schema       Discovered database SchemaMetadata
     * @param targetTables List of table names to connect
     * @return JoinPathResult indicating SUCCESS, AMBIGUOUS, or NO_PATH with join conditions
     */
    public static JoinPathResult resolveJoinPath(SchemaMetadata schema, List<String> targetTables) {
        if (schema == null || targetTables == null || targetTables.size() <= 1) {
            return new JoinPathResult(JoinPathResult.PathStatus.SUCCESS, Collections.emptyList(), "No join needed");
        }

        Map<String, List<Edge>> adjacency = buildGraph(schema);
        Set<String> connectedTables = new LinkedHashSet<>();
        List<JoinCondition> joins = new ArrayList<>();

        String primaryTable = targetTables.get(0);
        connectedTables.add(primaryTable);

        for (int i = 1; i < targetTables.size(); i++) {
            String target = targetTables.get(i);
            if (connectedTables.contains(target)) continue;

            List<Edge> shortestPath = null;
            int shortestLength = Integer.MAX_VALUE;
            int pathsWithShortestLength = 0;

            for (String start : connectedTables) {
                List<List<Edge>> paths = findAllShortestPaths(start, target, adjacency);
                if (!paths.isEmpty()) {
                    int len = paths.get(0).size();
                    if (len < shortestLength) {
                        shortestLength = len;
                        shortestPath = paths.get(0);
                        pathsWithShortestLength = paths.size();
                    } else if (len == shortestLength) {
                        pathsWithShortestLength += paths.size();
                    }
                }
            }

            if (shortestPath == null) {
                return new JoinPathResult(
                        JoinPathResult.PathStatus.NO_PATH,
                        null,
                        "No relationship path found in schema connecting tables: " + connectedTables + " and " + target
                );
            }

            if (pathsWithShortestLength > 1) {
                return new JoinPathResult(
                        JoinPathResult.PathStatus.AMBIGUOUS,
                        null,
                        "Multiple distinct join paths exist in schema between " + primaryTable + " and " + target
                );
            }

            for (Edge edge : shortestPath) {
                connectedTables.add(edge.getSourceTable());
                connectedTables.add(edge.getTargetTable());
                JoinCondition jc = new JoinCondition(
                        edge.getSourceTable(), edge.getSourceColumn(),
                        edge.getTargetTable(), edge.getTargetColumn(),
                        JoinType.INNER
                );
                if (!containsJoin(joins, jc)) {
                    joins.add(jc);
                }
            }
        }

        return new JoinPathResult(JoinPathResult.PathStatus.SUCCESS, joins, "Join path resolved successfully");
    }

    private static boolean containsJoin(List<JoinCondition> joins, JoinCondition jc) {
        for (JoinCondition existing : joins) {
            if ((existing.getLeftTable().equalsIgnoreCase(jc.getLeftTable()) &&
                 existing.getLeftColumn().equalsIgnoreCase(jc.getLeftColumn()) &&
                 existing.getRightTable().equalsIgnoreCase(jc.getRightTable()) &&
                 existing.getRightColumn().equalsIgnoreCase(jc.getRightColumn())) ||
                (existing.getLeftTable().equalsIgnoreCase(jc.getRightTable()) &&
                 existing.getLeftColumn().equalsIgnoreCase(jc.getRightColumn()) &&
                 existing.getRightTable().equalsIgnoreCase(jc.getLeftTable()) &&
                 existing.getRightColumn().equalsIgnoreCase(jc.getLeftColumn()))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, List<Edge>> buildGraph(SchemaMetadata schema) {
        Map<String, List<Edge>> graph = new HashMap<>();
        for (TableMetadata table : schema.getTables().values()) {
            String sourceTableName = table.getTableName();
            graph.putIfAbsent(sourceTableName.toLowerCase(), new ArrayList<>());

            for (ForeignKeyMetadata fk : table.getForeignKeys()) {
                String fkCol = fk.getFkColumnName();
                String pkTable = fk.getPkTableName();
                String pkCol = fk.getPkColumnName();

                graph.putIfAbsent(pkTable.toLowerCase(), new ArrayList<>());

                Edge forward = new Edge(sourceTableName, fkCol, pkTable, pkCol);
                Edge backward = new Edge(pkTable, pkCol, sourceTableName, fkCol);

                graph.get(sourceTableName.toLowerCase()).add(forward);
                graph.get(pkTable.toLowerCase()).add(backward);
            }
        }
        return graph;
    }

    private static List<List<Edge>> findAllShortestPaths(String start, String target, Map<String, List<Edge>> graph) {
        List<List<Edge>> result = new ArrayList<>();
        if (!graph.containsKey(start.toLowerCase()) || !graph.containsKey(target.toLowerCase())) {
            return result;
        }

        Queue<List<Edge>> queue = new LinkedList<>();
        queue.add(new ArrayList<>());

        Map<String, Integer> visitedDistance = new HashMap<>();
        visitedDistance.put(start.toLowerCase(), 0);

        int minDistanceToTarget = Integer.MAX_VALUE;

        while (!queue.isEmpty()) {
            List<Edge> currentPath = queue.poll();
            String currentTable = currentPath.isEmpty() ? start.toLowerCase() : currentPath.get(currentPath.size() - 1).getTargetTable().toLowerCase();

            if (currentPath.size() > minDistanceToTarget) {
                continue;
            }

            if (currentTable.equalsIgnoreCase(target.toLowerCase())) {
                if (currentPath.size() < minDistanceToTarget) {
                    minDistanceToTarget = currentPath.size();
                    result.clear();
                }
                result.add(currentPath);
                continue;
            }

            List<Edge> neighbors = graph.getOrDefault(currentTable, Collections.emptyList());
            for (Edge edge : neighbors) {
                String nextTable = edge.getTargetTable().toLowerCase();
                int nextDist = currentPath.size() + 1;

                if (!visitedDistance.containsKey(nextTable) || visitedDistance.get(nextTable) >= nextDist) {
                    visitedDistance.put(nextTable, nextDist);
                    List<Edge> newPath = new ArrayList<>(currentPath);
                    newPath.add(edge);
                    queue.add(newPath);
                }
            }
        }

        return result;
    }
}
