package com.nl2sql.answer;

import com.nl2sql.executor.QueryResult;
import com.nl2sql.nlp.ParsedQuestion;

/**
 * Interface defining the Natural-Language Answer Generation layer contract.
 * Converts structured ParsedQuestion and QueryResult objects into natural language responses.
 */
public interface AnswerGenerator {

    /**
     * Generates a deterministic, anti-hallucinatory natural language response from query results.
     *
     * @param parsedQuestion Parsed question contract from NLP layer
     * @param queryResult Execution result from DatabaseExecutor
     * @return AnswerResult containing the final natural language answer text
     */
    AnswerResult generate(ParsedQuestion parsedQuestion, QueryResult queryResult);
}
