package com.nl2sql.executor;

import com.nl2sql.database.DatabaseManager;
import com.nl2sql.generator.GeneratedQuery;
import com.nl2sql.validator.SQLValidationResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of DatabaseExecutor.
 * Executes validated SQL queries using PreparedStatement and returns generic QueryResult objects.
 */
public class DefaultDatabaseExecutor implements DatabaseExecutor {

    private final DatabaseManager databaseManager;

    public DefaultDatabaseExecutor(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public QueryResult execute(GeneratedQuery query, SQLValidationResult validationResult) {
        // 1. Fail closed safety checks
        if (query == null) {
            return QueryResult.failure("Execution Rejected: GeneratedQuery is null");
        }
        if (!query.isSuccess()) {
            return QueryResult.failure("Execution Rejected: GeneratedQuery was unsuccessful (" + query.getErrorMessage() + ")");
        }
        if (validationResult == null) {
            return QueryResult.failure("Execution Rejected: SQLValidationResult is null");
        }
        if (!validationResult.isValid()) {
            return QueryResult.failure("Execution Rejected: Query failed validation (" + validationResult.getReason() + ")");
        }
        if (query.getSql() == null || query.getSql().trim().isEmpty()) {
            return QueryResult.failure("Execution Rejected: SQL text is missing or empty");
        }

        // 2. Perform safe PreparedStatement execution
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(query.getSql())) {
                
                // Bind positional parameters
                List<Object> params = query.getParameters();
                if (params != null) {
                    for (int i = 0; i < params.size(); i++) {
                        Object param = params.get(i);
                        ps.setObject(i + 1, param);
                    }
                }

                // Execute query and extract result rows using metadata
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    List<String> columnNames = new ArrayList<>(colCount);
                    for (int i = 1; i <= colCount; i++) {
                        columnNames.add(meta.getColumnLabel(i));
                    }

                    List<List<Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        List<Object> row = new ArrayList<>(colCount);
                        for (int i = 1; i <= colCount; i++) {
                            Object value = rs.getObject(i); // Handles null values properly
                            row.add(value);
                        }
                        rows.add(row);
                    }

                    return new QueryResult(true, null, columnNames, rows);
                }
            }
        } catch (SQLException e) {
            return QueryResult.failure("Database Execution Error: " + e.getMessage());
        }
    }
}
