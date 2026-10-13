package com.nl2sql.generator;

import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.nlp.ParsedQuestion;

/**
 * Interface defining the SQL Generation stage contract.
 * Transforms a ParsedQuestion into a structured GeneratedQuery verified against SchemaMetadata.
 */
public interface SQLQueryGenerator {

    /**
     * Generates a parameterized SQL SELECT query from a ParsedQuestion.
     *
     * @param parsedQuestion Structured intent and entity representation from NLP phase
     * @param schemaMetadata Verified database catalog metadata from Phase 3
     * @return GeneratedQuery containing parameterized SQL, parameters, and status
     */
    GeneratedQuery generate(ParsedQuestion parsedQuestion, SchemaMetadata schemaMetadata);
}
