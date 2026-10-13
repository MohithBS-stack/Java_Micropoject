package com.nl2sql.nlp;

/**
 * Represents a extracted entity from a natural language question.
 */
public class Entity {
    private final String type;
    private final Object value;
    private final String sourceText;

    public Entity(String type, Object value, String sourceText) {
        this.type = type;
        this.value = value;
        this.sourceText = sourceText;
    }

    public String getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public String getSourceText() {
        return sourceText;
    }

    @Override
    public String toString() {
        return type + " = " + value + " (from '" + sourceText + "')";
    }
}
