package com.nl2sql.domain.chemistry.validation;

import org.apache.poi.ss.usermodel.*;
import java.io.InputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * Structural and data-integrity validator for chemistry datasets.
 * Reads Excel workbook files (.xlsx) dynamically without inventing chemistry facts or modifying source data.
 */
public class ChemistryDatasetValidator {

    /**
     * Validates a target Excel workbook file.
     *
     * @param workbookFile Target .xlsx File
     * @return ValidationReport detailing discovered sheets, columns, relationships, and integrity issues
     */
    public ValidationReport validate(File workbookFile) {
        if (workbookFile == null || !workbookFile.exists()) {
            List<ValidationIssue> issues = List.of(new ValidationIssue(ValidationIssue.Severity.ERROR, null, 0, null, "Workbook file does not exist: " + workbookFile));
            return new ValidationReport(workbookFile != null ? workbookFile.getPath() : "null", Collections.emptyMap(), Collections.emptyMap(), issues, ValidationReport.Status.ERROR);
        }

        try (InputStream is = new FileInputStream(workbookFile);
             Workbook workbook = WorkbookFactory.create(is)) {

            return validateWorkbook(workbook, workbookFile.getPath());

        } catch (Exception e) {
            List<ValidationIssue> issues = List.of(new ValidationIssue(ValidationIssue.Severity.ERROR, null, 0, null, "Failed to read Excel workbook: " + e.getMessage()));
            return new ValidationReport(workbookFile.getPath(), Collections.emptyMap(), Collections.emptyMap(), issues, ValidationReport.Status.ERROR);
        }
    }

    /**
     * Validates an open Apache POI Workbook instance.
     */
    /**
     * Overloaded method to validate a workbook from an InputStream.
     * Useful for tests that construct an in-memory workbook.
     */
    public ValidationReport validateWorkbook(InputStream is) {
        try (Workbook workbook = WorkbookFactory.create(is)) {
            return validateWorkbook(workbook, "InputStream");
        } catch (Exception e) {
            List<ValidationIssue> issues = List.of(new ValidationIssue(ValidationIssue.Severity.ERROR, null, 0, null, "Failed to read Excel workbook from InputStream: " + e.getMessage()));
            return new ValidationReport("InputStream", Collections.emptyMap(), Collections.emptyMap(), issues, ValidationReport.Status.ERROR);
        }
    }

    public ValidationReport validateWorkbook(Workbook workbook, String sourcePath) {
        Map<String, List<String>> sheetColumnsMap = new LinkedHashMap<>();
        Map<String, Integer> sheetRowCountsMap = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> sheetDataMap = new LinkedHashMap<>();
        List<ValidationIssue> issues = new ArrayList<>();

        int numSheets = workbook.getNumberOfSheets();
        for (int s = 0; s < numSheets; s++) {
            Sheet sheet = workbook.getSheetAt(s);
            String sheetName = sheet.getSheetName();

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, sheetName, 0, null, "Sheet is completely empty (no header row)"));
                sheetColumnsMap.put(sheetName, Collections.emptyList());
                sheetRowCountsMap.put(sheetName, 0);
                sheetDataMap.put(sheetName, Collections.emptyList());
                continue;
            }

            List<String> columns = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                String colName = (cell != null) ? getCellValueAsString(cell).trim() : "";
                if (!colName.isEmpty()) {
                    columns.add(colName);
                }
            }

            sheetColumnsMap.put(sheetName, columns);

            List<Map<String, Object>> rows = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue; // Skip empty rows
                }

                Map<String, Object> rowMap = new LinkedHashMap<>();
                for (int c = 0; c < columns.size(); c++) {
                    String colName = columns.get(c);
                    Cell cell = row.getCell(c);
                    Object cellValue = (cell != null) ? getCellValueAsObject(cell) : null;
                    rowMap.put(colName, cellValue);
                }
                rows.add(rowMap);
            }

            sheetRowCountsMap.put(sheetName, rows.size());
            sheetDataMap.put(sheetName, rows);
        }

        // Run Data Integrity & Relationship Validations
        performPrimaryIdentifierValidation(sheetColumnsMap, sheetDataMap, issues);
        performForeignKeyRelationshipValidation(sheetColumnsMap, sheetDataMap, issues);
        performDuplicateRelationshipValidation(sheetColumnsMap, sheetDataMap, issues);

        // Determine Overall Status
        ValidationReport.Status overallStatus = ValidationReport.Status.PASS;
        for (ValidationIssue issue : issues) {
            if (issue.getSeverity() == ValidationIssue.Severity.ERROR) {
                overallStatus = ValidationReport.Status.ERROR;
                break;
            } else if (issue.getSeverity() == ValidationIssue.Severity.WARNING && overallStatus != ValidationReport.Status.ERROR) {
                overallStatus = ValidationReport.Status.WARNING;
            }
        }

        return new ValidationReport(sourcePath, sheetColumnsMap, sheetRowCountsMap, issues, overallStatus);
    }

    /**
     * Validates primary keys, mandatory identifier fields, and duplicate IDs within each sheet.
     */
    private void performPrimaryIdentifierValidation(Map<String, List<String>> sheetColumnsMap,
                                                    Map<String, List<Map<String, Object>>> sheetDataMap,
                                                    List<ValidationIssue> issues) {
        for (Map.Entry<String, List<Map<String, Object>>> entry : sheetDataMap.entrySet()) {
            String sheetName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            List<String> columns = sheetColumnsMap.get(sheetName);

            if (columns == null || columns.isEmpty()) continue;

            // Identify potential primary key column (e.g., sheet_id or id or first column ending in _id)
            String primaryKeyCol = null;
            for (String col : columns) {
                if (col.equalsIgnoreCase(sheetName + "_id") || col.equalsIgnoreCase(singularize(sheetName) + "_id") || col.equalsIgnoreCase("id")) {
                    primaryKeyCol = col;
                    break;
                }
            }

            if (primaryKeyCol == null && !columns.isEmpty()) {
                String firstCol = columns.get(0);
                if (firstCol.toLowerCase().endsWith("_id")) {
                    primaryKeyCol = firstCol;
                }
            }

            if (primaryKeyCol != null) {
                Set<String> seenIds = new HashSet<>();
                for (int r = 0; r < rows.size(); r++) {
                    Map<String, Object> row = rows.get(r);
                    Object idValue = row.get(primaryKeyCol);

                    if (idValue == null || idValue.toString().trim().isEmpty()) {
                        issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, sheetName, r + 2, primaryKeyCol,
                                "Null or empty primary identifier in mandatory column '" + primaryKeyCol + "'"));
                    } else {
                        String idStr = idValue.toString().trim();
                        if (seenIds.contains(idStr)) {
                            issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, sheetName, r + 2, primaryKeyCol,
                                    "Duplicate primary key identifier detected: '" + idStr + "'"));
                        } else {
                            seenIds.add(idStr);
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates foreign key references across sheets.
     */
    private void performForeignKeyRelationshipValidation(Map<String, List<String>> sheetColumnsMap,
                                                          Map<String, List<Map<String, Object>>> sheetDataMap,
                                                          List<ValidationIssue> issues) {

        // Build primary key lookup map: targetSheet -> (targetCol -> Set of valid IDs)
        Map<String, Map<String, Set<String>>> pkCatalog = new HashMap<>();

        for (Map.Entry<String, List<Map<String, Object>>> entry : sheetDataMap.entrySet()) {
            String sheetName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            List<String> columns = sheetColumnsMap.get(sheetName);

            Map<String, Set<String>> colMap = new HashMap<>();
            if (columns != null) {
                for (String col : columns) {
                    if (col.toLowerCase().endsWith("_id")) {
                        Set<String> idSet = new HashSet<>();
                        for (Map<String, Object> row : rows) {
                            Object val = row.get(col);
                            if (val != null && !val.toString().trim().isEmpty()) {
                                idSet.add(val.toString().trim());
                            }
                        }
                        colMap.put(col.toLowerCase(), idSet);
                    }
                }
            }
            pkCatalog.put(sheetName.toLowerCase(), colMap);
        }

        // Validate FK columns in every sheet against target sheets
        for (Map.Entry<String, List<Map<String, Object>>> entry : sheetDataMap.entrySet()) {
            String sheetName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            List<String> columns = sheetColumnsMap.get(sheetName);

            if (columns == null) continue;

            for (String col : columns) {
                String lowerCol = col.toLowerCase();
                // Determine if this column is a foreign key (e.g. test_id in Salt_Tests, reagent_id in Test_Reagents)
                if (lowerCol.endsWith("_id")) {
                    String targetEntity = lowerCol.substring(0, lowerCol.length() - 3);
                    String targetSheetName = findMatchingSheetName(targetEntity, sheetColumnsMap.keySet());

                    // If column points to a different table (not primary key of current table)
                    if (targetSheetName != null && !targetSheetName.equalsIgnoreCase(sheetName)) {
                        Map<String, Set<String>> targetColMap = pkCatalog.get(targetSheetName.toLowerCase());
                        Set<String> validTargetIds = targetColMap != null ? targetColMap.get(lowerCol) : null;

                        if (validTargetIds != null) {
                            for (int r = 0; r < rows.size(); r++) {
                                Object fkVal = rows.get(r).get(col);
                                if (fkVal != null && !fkVal.toString().trim().isEmpty()) {
                                    String fkStr = fkVal.toString().trim();
                                    if (!validTargetIds.contains(fkStr)) {
                                        issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, sheetName, r + 2, col,
                                                "Broken reference: '" + col + "' value '" + fkStr +
                                                        "' does not exist in target sheet '" + targetSheetName + "'"));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates duplicate relationship rows in junction sheets.
     */
    private void performDuplicateRelationshipValidation(Map<String, List<String>> sheetColumnsMap,
                                                        Map<String, List<Map<String, Object>>> sheetDataMap,
                                                        List<ValidationIssue> issues) {
        for (Map.Entry<String, List<Map<String, Object>>> entry : sheetDataMap.entrySet()) {
            String sheetName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            List<String> columns = sheetColumnsMap.get(sheetName);

            // Check if sheet contains multiple _id columns (indicative of junction table)
            long fkColCount = columns != null ? columns.stream().filter(c -> c.toLowerCase().endsWith("_id")).count() : 0;
            if (fkColCount >= 2) {
                Set<String> tupleSet = new HashSet<>();
                for (int r = 0; r < rows.size(); r++) {
                    Map<String, Object> row = rows.get(r);
                    StringBuilder sb = new StringBuilder();
                    for (String col : columns) {
                        if (col.toLowerCase().endsWith("_id")) {
                            sb.append(col).append("=").append(row.get(col)).append("|");
                        }
                    }
                    String tupleKey = sb.toString();
                    if (tupleSet.contains(tupleKey)) {
                        issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, sheetName, r + 2, null,
                                "Duplicate relationship tuple detected: " + tupleKey));
                    } else {
                        tupleSet.add(tupleKey);
                    }
                }
            }
        }
    }

    private String findMatchingSheetName(String entity, Set<String> availableSheets) {
        for (String sheet : availableSheets) {
            if (sheet.equalsIgnoreCase(entity) || sheet.equalsIgnoreCase(entity + "s") ||
                sheet.equalsIgnoreCase(singularize(entity)) || singularize(sheet).equalsIgnoreCase(entity)) {
                return sheet;
            }
        }
        return null;
    }

    private String singularize(String name) {
        if (name != null && name.toLowerCase().endsWith("s")) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getCellValueAsString(cell);
                if (val != null && !val.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private Object getCellValueAsObject(Cell cell) {
        switch (cell.getCellType()) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                }
                double d = cell.getNumericCellValue();
                if (d == (long) d) {
                    return (long) d;
                }
                return d;
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case STRING:
                return cell.getStringCellValue().trim();
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    return cell.getNumericCellValue();
                }
            default:
                return null;
        }
    }

    private String getCellValueAsString(Cell cell) {
        Object val = getCellValueAsObject(cell);
        return val != null ? val.toString() : "";
    }
}
