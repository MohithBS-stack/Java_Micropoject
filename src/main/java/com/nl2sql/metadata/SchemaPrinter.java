package com.nl2sql.metadata;

import com.nl2sql.database.DatabaseContext;

import java.util.List;

/**
 * Utility for formatting discovered database schema metadata according to Phase 14 specifications.
 */
public class SchemaPrinter {

    /**
     * Formats schema metadata for an active DatabaseContext into a clean text inspection report.
     *
     * @param context Active DatabaseContext
     * @return Formatted inspection report string
     */
    public static String formatSchemaInspection(DatabaseContext context) {
        if (context == null) {
            return "DATABASE:\n(null context)";
        }
        return formatSchemaInspection(context.getDbPath(), context.getSchemaMetadata());
    }

    /**
     * Formats schema metadata for a database path and SchemaMetadata graph.
     *
     * @param dbPath Path to the database
     * @param schemaMetadata Discovered schema metadata
     * @return Formatted inspection report string
     */
    public static String formatSchemaInspection(String dbPath, SchemaMetadata schemaMetadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("DATABASE:\n");
        sb.append(dbPath != null ? dbPath : "(unknown)").append("\n\n");

        sb.append("TABLES:\n");
        if (schemaMetadata == null || schemaMetadata.getTables().isEmpty()) {
            sb.append("(no tables found)\n");
            return sb.toString();
        }

        for (TableMetadata table : schemaMetadata.getTables().values()) {
            sb.append("- ").append(table.getTableName()).append("\n");
        }
        sb.append("\n");

        for (TableMetadata table : schemaMetadata.getTables().values()) {
            sb.append("TABLE: ").append(table.getTableName()).append("\n\n");

            sb.append("COLUMNS:\n");
            for (ColumnMetadata col : table.getColumns()) {
                sb.append("- ").append(col.getName())
                  .append(" | ").append(col.getDataType() != null ? col.getDataType() : "UNKNOWN")
                  .append(" | nullable=").append(col.isNullable())
                  .append(" | primary_key=").append(col.isPrimaryKey())
                  .append("\n");
            }
            sb.append("\n");

            sb.append("FOREIGN KEYS:\n");
            List<ForeignKeyMetadata> fks = table.getForeignKeys();
            if (fks.isEmpty()) {
                sb.append("(none)\n");
            } else {
                for (ForeignKeyMetadata fk : fks) {
                    sb.append("- ").append(table.getTableName()).append(".").append(fk.getFkColumnName())
                      .append(" -> ").append(fk.getPkTableName()).append(".").append(fk.getPkColumnName())
                      .append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }
}
