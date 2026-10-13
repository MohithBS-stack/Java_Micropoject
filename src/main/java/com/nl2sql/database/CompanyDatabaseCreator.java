package com.nl2sql.database;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility to generate a synthetic SQLite database (data/company.db)
 * to prove that the generic database foundation is non-domain specific.
 */
public class CompanyDatabaseCreator {

    public static final String DEFAULT_COMPANY_DB_PATH = "data/company.db";

    /**
     * Creates or resets the synthetic company SQLite database at default location ("data/company.db").
     *
     * @return Path to created database file
     * @throws SQLException If database creation or DDL/DML execution fails
     */
    public static String createCompanyDatabase() throws SQLException {
        createCompanyDatabase(DEFAULT_COMPANY_DB_PATH);
        return DEFAULT_COMPANY_DB_PATH;
    }

    /**
     * Creates or resets the synthetic company SQLite database at specified file path.
     *
     * @param dbPath Target SQLite database file path
     * @throws SQLException If database creation or DDL/DML execution fails
     */
    public static void createCompanyDatabase(String dbPath) throws SQLException {
        File dbFile = new File(dbPath);
        if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }

        try (DatabaseManager manager = new DatabaseManager(dbPath)) {
            Connection conn = manager.getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = OFF;");
                stmt.execute("DROP TABLE IF EXISTS employees;");
                stmt.execute("DROP TABLE IF EXISTS departments;");
                stmt.execute("PRAGMA foreign_keys = ON;");

                stmt.execute("CREATE TABLE departments (" +
                        "department_id INTEGER PRIMARY KEY, " +
                        "department_name TEXT NOT NULL" +
                        ");");

                stmt.execute("CREATE TABLE employees (" +
                        "employee_id INTEGER PRIMARY KEY, " +
                        "employee_name TEXT NOT NULL, " +
                        "department_id INTEGER, " +
                        "salary REAL, " +
                        "hire_date TEXT, " +
                        "FOREIGN KEY (department_id) REFERENCES departments(department_id)" +
                        ");");

                stmt.execute("INSERT INTO departments (department_id, department_name) VALUES " +
                        "(1, 'Engineering'), " +
                        "(2, 'Sales'), " +
                        "(3, 'Research');");

                stmt.execute("INSERT INTO employees (employee_id, employee_name, department_id, salary, hire_date) VALUES " +
                        "(101, 'Alice Smith', 1, 85000.0, '2022-03-15'), " +
                        "(102, 'Bob Jones', 1, 92000.0, '2021-06-01'), " +
                        "(103, 'Charlie Brown', 2, 65000.0, '2023-01-10'), " +
                        "(104, 'Diana Prince', 3, 110000.0, '2020-11-20');");
            }
        }
    }
}
