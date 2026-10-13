package com.nl2sql.executor;

import com.nl2sql.generator.GeneratedQuery;
import com.nl2sql.validator.SQLValidationResult;

/**
 * Interface defining the Database Execution layer contract.
 * Safely executes validated GeneratedQuery objects against SQLite.
 */
public interface DatabaseExecutor {

    /**
     * Executes a GeneratedQuery ONLY after confirming that SQLValidationResult is valid.
     * Fails closed if validation failed or inputs are invalid.
     *
     * @param query Parameterized query object
     * @param validationResult Output from SQLValidator
     * @return QueryResult containing column names, returned rows, and row count
     */
    QueryResult execute(GeneratedQuery query, SQLValidationResult validationResult);
}
