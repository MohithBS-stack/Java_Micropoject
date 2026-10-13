package com.nl2sql.answer;

import com.nl2sql.executor.QueryResult;

import java.util.List;

/**
 * Generic formatting helper that renders QueryResult column/value tuples into structured text.
 * Strictly avoids inventing facts or assuming schema column names.
 */
public class RowFormatter {

    /**
     * Formats rows from a QueryResult into a readable string representation.
     *
     * @param queryResult Execution result from database
     * @return Formatted string representation
     */
    public String format(QueryResult queryResult) {
        if (queryResult == null || !queryResult.isSuccess()) {
            return "No data available.";
        }

        List<String> columns = queryResult.getColumnNames();
        List<List<Object>> rows = queryResult.getRows();

        if (rows.isEmpty()) {
            return "No matching records were found in the database.";
        }

        StringBuilder sb = new StringBuilder();

        if (rows.size() == 1) {
            List<Object> row = rows.get(0);
            for (int i = 0; i < columns.size(); i++) {
                String colName = columns.get(i);
                Object val = (i < row.size()) ? row.get(i) : null;
                String valStr = (val == null) ? "NULL" : val.toString();
                sb.append(colName).append(": ").append(valStr);
                if (i < columns.size() - 1) {
                    sb.append("\n");
                }
            }
        } else {
            sb.append("Found ").append(rows.size()).append(" matching records:\n\n");
            for (int r = 0; r < rows.size(); r++) {
                List<Object> row = rows.get(r);
                sb.append(r + 1).append(". ");
                for (int c = 0; c < columns.size(); c++) {
                    String colName = columns.get(c);
                    Object val = (c < row.size()) ? row.get(c) : null;
                    String valStr = (val == null) ? "NULL" : val.toString();
                    if (c > 0) {
                        sb.append("\n   ");
                    }
                    sb.append(colName).append(": ").append(valStr);
                }
                if (r < rows.size() - 1) {
                    sb.append("\n\n");
                }
            }
        }

        return sb.toString();
    }
}
