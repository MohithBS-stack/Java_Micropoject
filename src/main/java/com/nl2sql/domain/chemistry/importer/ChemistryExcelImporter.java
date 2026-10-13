package com.nl2sql.domain.chemistry.importer;

import com.nl2sql.database.DatabaseManager;
import com.nl2sql.database.SchemaInitializer;
import com.nl2sql.domain.chemistry.ChemistrySchemaInitializer;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Mapping of Excel header names to actual database column names for tables where they differ.
 * Key: lower‑case table name.
 * Value: map of Excel column -> DB column.
 */


/**
 * Imports a validated chemistry Excel workbook into a dedicated SQLite database.
 * The importer creates tables that match the workbook sheets. If a table already exists (via the
 * ChemistrySchemaInitializer), it will be reused; otherwise a generic TEXT‑only table is created.
 * The whole import runs inside a single transaction and rolls back on any error.
 */
public class ChemistryExcelImporter {
    private final DatabaseManager dbManager;
    private final File excelFile;
    private static class TargetColumnMapping {
        final String targetColName;
        final int excelColIndex;

        TargetColumnMapping(String targetColName, int excelColIndex) {
            this.targetColName = targetColName;
            this.excelColIndex = excelColIndex;
        }
    }

    private static final Map<String, Map<String, String>> COLUMN_RENAME_MAP = Map.of(
        "analytes", Map.of(
            "symbol", "symbol_formula"
        ),
        "tests", Map.of(
            "test_category", "test_type",
            "procedure", "procedure_description"
        ),
        "test_reagents", Map.of(
            "sequence_order", "addition_order",
            "quantity", "quantity_notes"
        )
    );

    public ChemistryExcelImporter(DatabaseManager dbManager, File excelFile) {
        this.dbManager = dbManager;
        this.excelFile = excelFile;
    }

    /**
     * Executes the import and returns a detailed report.
     */
    public ChemistryImportReport importData() throws Exception {
        ChemistryImportReport report = new ChemistryImportReport();
        Connection conn = dbManager.getConnection();
        // Enable foreign keys
        try (PreparedStatement pragma = conn.prepareStatement("PRAGMA foreign_keys = ON")) {
            pragma.execute();
        }
        // Ensure the chemistry schema (core tables) exists first
        SchemaInitializer schemaInitializer = new ChemistrySchemaInitializer();
        schemaInitializer.initializeSchema(dbManager);

        // Begin transaction
        conn.setAutoCommit(false);
        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            int sheetCount = workbook.getNumberOfSheets();
            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    // empty sheet – report and continue
                    ChemistryImportReport.SheetReport sr = new ChemistryImportReport.SheetReport(sheetName, "<none>", 0);
                    sr.skippedRowCount = 0;
                    sr.importedRowCount = 0;
                    sr.failedRowCount = 0;
                    sr.failureReasons.add("Sheet has no header row – skipped.");
                    report.addSheetReport(sr);
                    continue;
                }

                String targetTable = sheetName.toLowerCase();
                boolean tableExists = doesTableExist(conn, targetTable);
                Map<String, String> renameMap = COLUMN_RENAME_MAP.get(targetTable);

                Set<String> existingCols = new HashSet<>();
                if (tableExists) {
                    try (Statement stmt2 = conn.createStatement();
                         ResultSet rs2 = stmt2.executeQuery("PRAGMA table_info(" + targetTable + ")")) {
                        while (rs2.next()) {
                            existingCols.add(rs2.getString("name").toLowerCase());
                        }
                    }
                }

                List<TargetColumnMapping> targetColumns = new ArrayList<>();
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    Cell cell = headerRow.getCell(c);
                    String rawColName = (cell != null) ? getCellValueAsString(cell).trim() : "";
                    if (rawColName.isEmpty()) {
                        continue;
                    }
                    String colName = rawColName;
                    if (renameMap != null && renameMap.containsKey(colName.toLowerCase())) {
                        colName = renameMap.get(colName.toLowerCase());
                    }

                    if (tableExists) {
                        if (existingCols.contains(colName.toLowerCase())) {
                            targetColumns.add(new TargetColumnMapping(colName, c));
                        }
                    } else {
                        targetColumns.add(new TargetColumnMapping(colName, c));
                    }
                }

                if (targetColumns.isEmpty()) {
                    ChemistryImportReport.SheetReport sr = new ChemistryImportReport.SheetReport(sheetName, targetTable, 0);
                    sr.skippedRowCount = 0;
                    sr.importedRowCount = 0;
                    sr.failedRowCount = 0;
                    sr.failureReasons.add("No matching columns after schema filtering – sheet skipped.");
                    report.addSheetReport(sr);
                    continue;
                }

                List<String> colNames = targetColumns.stream().map(m -> m.targetColName).toList();
                ensureTableExists(conn, targetTable, colNames);

                String placeholders = String.join(", ", Collections.nCopies(targetColumns.size(), "?"));
                String colList = String.join(", ", colNames);
                String sql = "INSERT INTO " + targetTable + " (" + colList + ") VALUES (" + placeholders + ")";

                try (PreparedStatement insertStmt = conn.prepareStatement(sql)) {
                    int sourceRowCount = 0;
                    int imported = 0;
                    int failed = 0;
                    int skipped = 0;
                    ChemistryImportReport.SheetReport sheetReport = new ChemistryImportReport.SheetReport(sheetName, targetTable, 0);
                    int lastRowNum = sheet.getLastRowNum();
                    for (int r = 1; r <= lastRowNum; r++) {
                        Row row = sheet.getRow(r);
                        if (row == null || isRowEmpty(row)) {
                            continue; // skip empty rows
                        }
                        sourceRowCount++;
                        // Bind values
                        for (int c = 0; c < targetColumns.size(); c++) {
                            TargetColumnMapping mapping = targetColumns.get(c);
                            Cell cell = row.getCell(mapping.excelColIndex);
                            if (cell == null) {
                                insertStmt.setObject(c + 1, null);
                                continue;
                            }
                            switch (cell.getCellType()) {
                                case STRING:
                                    insertStmt.setString(c + 1, cell.getStringCellValue());
                                    break;
                                case NUMERIC:
                                    if (DateUtil.isCellDateFormatted(cell)) {
                                        insertStmt.setObject(c + 1, cell.getDateCellValue());
                                    } else {
                                        double d = cell.getNumericCellValue();
                                        // Store as integer when possible
                                        if (d == (long) d) {
                                            insertStmt.setLong(c + 1, (long) d);
                                        } else {
                                            insertStmt.setDouble(c + 1, d);
                                        }
                                    }
                                    break;
                                case BOOLEAN:
                                    insertStmt.setBoolean(c + 1, cell.getBooleanCellValue());
                                    break;
                                case BLANK:
                                    insertStmt.setObject(c + 1, null);
                                    break;
                                default:
                                    insertStmt.setString(c + 1, getCellValueAsString(cell));
                                    break;
                            }
                        }
                        try {
                            insertStmt.executeUpdate();
                            imported++;
                        } catch (SQLException ex) {
                            failed++;
                            sheetReport.failureReasons.add("Row " + (r + 1) + ": " + ex.getMessage());
                            // any error aborts the whole import – rethrow to trigger rollback
                            throw ex;
                        }
                    }
                    sheetReport.sourceRowCount = sourceRowCount;
                    sheetReport.importedRowCount = imported;
                    sheetReport.failedRowCount = failed;
                    sheetReport.skippedRowCount = skipped;
                    report.addSheetReport(sheetReport);
                }
            }
            // Commit transaction if we reach here
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
        return report;
    }

    private void ensureTableExists(Connection conn, String tableName, List<String> columns) throws SQLException {
        // Quick check using SQLite master table – if exists, do nothing
        if (doesTableExist(conn, tableName)) {
            return;
        }
        // If there are no columns to create, skip table creation
        if (columns == null || columns.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(tableName).append(" (");
        List<String> defs = new ArrayList<>();
        for (String col : columns) {
            String def = col + " TEXT";
            if (col.equalsIgnoreCase(tableName + "_id") || col.equalsIgnoreCase("id")) {
                def += " PRIMARY KEY";
            }
            defs.add(def);
        }
        sb.append(String.join(", ", defs));
        sb.append(")");
        try (PreparedStatement create = conn.prepareStatement(sb.toString())) {
            create.execute();
        }
    }

    /**
     * Helper to check if a table already exists in the SQLite database.
     */
    private boolean doesTableExist(Connection conn, String tableName) throws SQLException {
        String checkSql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, tableName);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double d = cell.getNumericCellValue();
                if (d == (long) d) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
}
