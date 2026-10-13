package com.nl2sql.nlp;

import com.nl2sql.metadata.SchemaMetadata;

/**
 * Interface defining the Question Understanding component contract.
 * Allows rule-based or future LLM-based parsers to be plugged into the NL-to-SQL pipeline.
 */
public interface QuestionParser {
    
    /**
     * Parses a raw natural language question into a structured representation.
     *
     * @param question Natural language input text
     * @param schemaMetadata Current database schema metadata (optional for schema-guided extraction)
     * @return ParsedQuestion instance with intent, entities, and ambiguity status
     */
    ParsedQuestion parse(String question, SchemaMetadata schemaMetadata);
}
