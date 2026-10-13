package com.nl2sql.plan;

/**
 * Indicates the resolution outcome of a natural-language question.
 */
public enum QueryPlanStatus {
    /**
     * Query successfully resolved into a valid, unambiguous intermediate QueryPlan.
     */
    SUPPORTED,

    /**
     * Query matches multiple tables/columns or join paths without sufficient disambiguation.
     */
    AMBIGUOUS,

    /**
     * Query cannot be matched to the schema or contains unsupported natural-language concepts.
     */
    UNSUPPORTED
}
