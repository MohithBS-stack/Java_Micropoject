package com.nl2sql.routing;

/**
 * Configuration manager for online LLM API integration.
 * Reads API key securely from environment variables without hardcoding secrets.
 */
public class LLMConfiguration {

    public static final String DEFAULT_ENV_VAR = "GEMINI_API_KEY";

    private final String apiKey;
    private final String modelName;

    public LLMConfiguration() {
        this(System.getenv(DEFAULT_ENV_VAR), "gemini-2.5-flash");
    }

    public LLMConfiguration(String apiKey, String modelName) {
        this.apiKey = apiKey != null ? apiKey.trim() : null;
        this.modelName = modelName;
    }

    /**
     * Checks whether a valid non-empty API key is configured.
     *
     * @return true if API key is present, false otherwise
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModelName() {
        return modelName;
    }
}
