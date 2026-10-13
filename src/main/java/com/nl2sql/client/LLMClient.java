package com.nl2sql.client;

/**
 * Interface abstracting raw REST interaction with external LLM APIs.
 * Separates transport/HTTP mechanics from natural language parsing logic.
 */
public interface LLMClient {

    /**
     * Sends a text prompt to the underlying LLM service and returns the raw text/JSON response.
     *
     * @param prompt Text prompt containing user question and system instructions
     * @return Raw text or JSON response from the LLM service
     * @throws Exception If network, timeout, HTTP error, or authentication failure occurs
     */
    String generateContent(String prompt) throws Exception;
}
