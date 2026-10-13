package com.nl2sql.plan;

/**
 * High-level intent of the natural-language question.
 */
public enum QueryIntent {
    SELECT,
    COUNT,
    AGGREGATE,
    UNKNOWN
}
