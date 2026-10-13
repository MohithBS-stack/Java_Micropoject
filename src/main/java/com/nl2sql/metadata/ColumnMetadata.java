package com.nl2sql.metadata;

/**
 * Represents metadata for a single database column.
 */
public class ColumnMetadata {
    private final String name;
    private final String dataType;
    private final boolean primaryKey;
    private final boolean nullable;

    public ColumnMetadata(String name, String dataType, boolean primaryKey, boolean nullable) {
        this.name = name;
        this.dataType = dataType;
        this.primaryKey = primaryKey;
        this.nullable = nullable;
    }

    public String getName() {
        return name;
    }

    public String getDataType() {
        return dataType;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public boolean isNullable() {
        return nullable;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("COLUMN: ").append(name).append(" | ").append(dataType);
        if (primaryKey) {
            sb.append(" | PRIMARY KEY");
        }
        if (!nullable) {
            sb.append(" | NOT NULL");
        }
        return sb.toString();
    }
}
