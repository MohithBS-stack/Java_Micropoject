package com.nl2sql.domain.chemistry.importer;

import com.nl2sql.database.DatabaseManager;
import com.nl2sql.domain.chemistry.validation.ChemistryDatasetValidator;
import com.nl2sql.domain.chemistry.validation.ValidationReport;
import java.io.File;

/**
 * Simple runner for the chemistry Excel → SQLite import process.
 * It is intended to be invoked via the "--import-chemistry" command‑line flag in Main.
 */
public class ChemistryImportRunner {
    private static final String CHEMISTRY_DB_PATH = "data/chemistry.db";
    private static final String EXCEL_WORKBOOK_PATH = "salt_analysis_database.xlsx";

    public static void run() {
        System.out.println("=== Starting Chemistry Dataset Import ===");
        // Ensure the target DB file is fresh – delete before opening connection
        java.io.File dbFile = new java.io.File(CHEMISTRY_DB_PATH);
        if (dbFile.exists()) {
            if (!dbFile.delete()) {
                System.err.println("WARNING: Could not delete existing chemistry.db – proceeding with existing file.");
            }
        }
        try (DatabaseManager dbManager = new DatabaseManager(CHEMISTRY_DB_PATH)) {

            File workbookFile = new File(EXCEL_WORKBOOK_PATH);
            ChemistryExcelImporter importer = new ChemistryExcelImporter(dbManager, workbookFile);
            ChemistryImportReport report = importer.importData();
            System.out.println(report.getFormattedReport());

            // Post‑import validation
            PostImportValidator postValidator = new PostImportValidator(dbManager);
            ValidationReport postReport = postValidator.validate(report);
            System.out.println("--- Post‑Import Validation Report ---");
            System.out.println(postReport.getFormattedReport());
        } catch (Exception e) {
            System.err.println("ERROR during chemistry import: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
