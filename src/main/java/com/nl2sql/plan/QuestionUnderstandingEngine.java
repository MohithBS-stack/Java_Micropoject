package com.nl2sql.plan;

import com.nl2sql.metadata.SchemaMetadata;

/**
 * Generic interface for parsing natural-language questions into an intermediate QueryPlan
 * using a discovered database schema.
 *
 * Designed for offline rule-based implementations as well as future LLM-backed engines.
 */
public interface QuestionUnderstandingEngine {

    /**
     * Translates a user's natural language question into a structured QueryPlan based on the provided schema metadata.
     *
     * @param question Natural language user query
     * @param schema   Discovered database SchemaMetadata
     * @return Resolved QueryPlan (SUPPORTED, AMBIGUOUS, or UNSUPPORTED)
     */
    QueryPlan understand(String question, SchemaMetadata schema);
}
