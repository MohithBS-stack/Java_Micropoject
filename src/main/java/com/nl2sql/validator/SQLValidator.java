package com.nl2sql.validator;

import com.nl2sql.generator.GeneratedQuery;
import com.nl2sql.metadata.SchemaMetadata;

/**
 * Interface defining the SQL Validation & Safety Layer contract.
 * Independently validates GeneratedQuery objects for execution safety, injection defense, and schema compliance.
 */
public interface SQLValidator {

    /**
     * Validates a GeneratedQuery object against strict security rules and SchemaMetadata catalog rules.
     *
     * @param query Untrusted GeneratedQuery instance
     * @param schemaMetadata Authoritative database schema metadata
     * @return SQLValidationResult detailing pass/fail status and rejection reasons
     */
    SQLValidationResult validate(GeneratedQuery query, SchemaMetadata schemaMetadata);
}
