package com.nl2sql.generator;

import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.plan.QueryPlan;

/**
 * Generic interface for transforming a domain-independent QueryPlan into a safe,
 * parameterized GeneratedQuery verified against SchemaMetadata.
 */
public interface QueryPlanSQLGenerator {

    /**
     * Translates a QueryPlan into a parameterized GeneratedQuery based on the target database schema metadata.
     *
     * @param plan   Domain-independent intermediate QueryPlan
     * @param schema Discovered database SchemaMetadata
     * @return GeneratedQuery containing parameterized SQL, parameters, and status
     */
    GeneratedQuery generate(QueryPlan plan, SchemaMetadata schema);
}
