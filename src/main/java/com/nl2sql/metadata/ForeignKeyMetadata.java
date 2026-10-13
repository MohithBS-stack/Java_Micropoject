package com.nl2sql.metadata;

/**
 * Represents a foreign key constraint linking a local column to a target primary key column.
 */
public class ForeignKeyMetadata {
    private final String fkColumnName;
    private final String pkTableName;
    private final String pkColumnName;

    public ForeignKeyMetadata(String fkColumnName, String pkTableName, String pkColumnName) {
        this.fkColumnName = fkColumnName;
        this.pkTableName = pkTableName;
        this.pkColumnName = pkColumnName;
    }

    public String getFkColumnName() {
        return fkColumnName;
    }

    public String getPkTableName() {
        return pkTableName;
    }

    public String getPkColumnName() {
        return pkColumnName;
    }

    @Override
    public String toString() {
        return "FOREIGN KEY: " + fkColumnName + " -> " + pkTableName + "(" + pkColumnName + ")";
    }
}
