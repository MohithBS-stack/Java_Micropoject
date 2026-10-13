package com.nl2sql.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ExcelInspector {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: ExcelInspector <excelFilePath>");
            return;
        }
        File file = new File(args[0]);
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            int sheetCount = workbook.getNumberOfSheets();
            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                Row header = sheet.getRow(0);
                List<String> cols = new ArrayList<>();
                if (header != null) {
                    for (int c = 0; c < header.getLastCellNum(); c++) {
                        Cell cell = header.getCell(c);
                        String colName = cell != null ? cell.getStringCellValue().trim() : "";
                        if (!colName.isEmpty()) {
                            cols.add(colName);
                        }
                    }
                }
                System.out.println("Sheet: " + sheetName);
                System.out.println("Columns: " + String.join(", ", cols));
                System.out.println();
            }
        }
    }
}
