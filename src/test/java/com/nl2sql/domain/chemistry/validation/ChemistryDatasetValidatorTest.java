package com.nl2sql.domain.chemistry.validation;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ChemistryDatasetValidatorTest {

    @Test
    public void testValidWorkbook() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet1 = wb.createSheet("Salts");
            Row r0 = sheet1.createRow(0);
            r0.createCell(0).setCellValue("salt_id");
            r0.createCell(1).setCellValue("salt_name");

            Row r1 = sheet1.createRow(1);
            r1.createCell(0).setCellValue(1.0); // POI numeric
            r1.createCell(1).setCellValue("Sodium Chloride");

            Sheet sheet2 = wb.createSheet("Salt_Tests");
            Row s2r0 = sheet2.createRow(0);
            s2r0.createCell(0).setCellValue("salt_id");
            s2r0.createCell(1).setCellValue("test_id");

            Row s2r1 = sheet2.createRow(1);
            s2r1.createCell(0).setCellValue(1.0);
            s2r1.createCell(1).setCellValue(10.0);

            byte[] bytes = toBytes(wb);
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                ChemistryDatasetValidator validator = new ChemistryDatasetValidator();
                ValidationReport report = validator.validateWorkbook(is);
                assertNotNull(report);
                assertEquals(2, report.getDetectedSheets().size());
                assertFalse(report.hasErrors());
            }
        }
    }

    @Test
    public void testDuplicatePrimaryKeysAndBrokenForeignKeys() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            // Parent sheet with duplicate ID
            Sheet parent = wb.createSheet("Salts");
            Row pr0 = parent.createRow(0);
            pr0.createCell(0).setCellValue("salt_id");
            pr0.createCell(1).setCellValue("salt_name");

            Row pr1 = parent.createRow(1);
            pr1.createCell(0).setCellValue("SALT_01");
            pr1.createCell(1).setCellValue("Salt One");

            Row pr2 = parent.createRow(2);
            pr2.createCell(0).setCellValue("SALT_01"); // Duplicate PK
            pr2.createCell(1).setCellValue("Salt One Dup");

            // Child sheet referencing non-existent parent
            Sheet child = wb.createSheet("Salt_Tests");
            Row cr0 = child.createRow(0);
            cr0.createCell(0).setCellValue("salt_id");
            cr0.createCell(1).setCellValue("test_id");

            Row cr1 = child.createRow(1);
            cr1.createCell(0).setCellValue("SALT_99"); // Broken FK (SALT_99 not in Salts)
            cr1.createCell(1).setCellValue("TEST_01");

            byte[] bytes = toBytes(wb);
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                ChemistryDatasetValidator validator = new ChemistryDatasetValidator();
                ValidationReport report = validator.validateWorkbook(is);
                assertTrue(report.hasErrors());

                boolean foundDupError = report.getIssues().stream()
                        .anyMatch(i -> i.getSheetName().equals("Salts") && i.getMessage().contains("Duplicate primary key"));
                boolean foundFkError = report.getIssues().stream()
                        .anyMatch(i -> i.getSheetName().equals("Salt_Tests") && i.getMessage().contains("Broken reference"));

                assertTrue(foundDupError, "Should report duplicate primary key error");
                assertTrue(foundFkError, "Should report broken foreign key reference error");
            }
        }
    }

    private byte[] toBytes(Workbook wb) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wb.write(baos);
        return baos.toByteArray();
    }
}
