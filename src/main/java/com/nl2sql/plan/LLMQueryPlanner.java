package com.nl2sql.plan;

import com.nl2sql.client.GeminiLLMClient;
import com.nl2sql.client.LLMClient;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.routing.ConnectivityChecker;
import com.nl2sql.routing.DefaultConnectivityChecker;
import com.nl2sql.routing.LLMConfiguration;

/**
 * Online LLM-backed QuestionUnderstandingEngine.
 * Integrates Gemini API to translate natural language questions into structured QueryPlans using dynamic SchemaMetadata.
 * Employs automatic offline fallback to RuleBasedQueryPlanner on API failures or network unavailability.
 */
public class LLMQueryPlanner implements QuestionUnderstandingEngine {

    private final LLMClient llmClient;
    private final ConnectivityChecker connectivityChecker;
    private final QuestionUnderstandingEngine fallbackPlanner;

    public LLMQueryPlanner(LLMConfiguration configuration) {
        this(new GeminiLLMClient(configuration), new DefaultConnectivityChecker(), new RuleBasedQueryPlanner());
    }

    public LLMQueryPlanner(LLMClient llmClient) {
        this(llmClient, (llmClient instanceof com.nl2sql.client.MockLLMClient) ? () -> true : new DefaultConnectivityChecker(), new RuleBasedQueryPlanner());
    }

    public LLMQueryPlanner(LLMClient llmClient, ConnectivityChecker connectivityChecker, QuestionUnderstandingEngine fallbackPlanner) {
        this.llmClient = llmClient;
        this.connectivityChecker = connectivityChecker != null ? connectivityChecker : new DefaultConnectivityChecker();
        this.fallbackPlanner = fallbackPlanner != null ? fallbackPlanner : new RuleBasedQueryPlanner();
    }

    @Override
    public QueryPlan understand(String question, SchemaMetadata schema) {
        if (question == null || question.trim().isEmpty() || schema == null) {
            return fallbackPlanner.understand(question, schema);
        }

        // Check network connectivity and LLM availability
        if (llmClient == null || !connectivityChecker.isInternetAvailable()) {
            return fallbackPlanner.understand(question, schema);
        }

        try {
            String prompt = buildSystemPrompt(question, schema);
            String rawResponse = llmClient.generateContent(prompt);
            return LLMQueryPlanMapper.parseAndValidate(rawResponse, question, schema);
        } catch (Exception e) {
            // Fallback seamlessly on API errors, timeouts, or unparseable responses
            return fallbackPlanner.understand(question, schema);
        }
    }

    private String buildSystemPrompt(String question, SchemaMetadata schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a natural language database query planner.\n");
        sb.append("Analyze the user question and database schema below to produce a structured JSON QueryPlan.\n\n");
        sb.append("CRITICAL RULES:\n");
        sb.append("1. Only use tables, columns, and foreign keys provided in the schema below.\n");
        sb.append("2. Never invent tables, columns, or relationships.\n");
        sb.append("3. Never generate SQL syntax or raw SQL queries.\n");
        sb.append("4. Never answer with factual row values.\n");
        sb.append("5. If the question cannot be answered using the schema, return \"status\": \"UNSUPPORTED\".\n");
        sb.append("6. If multiple interpretations are possible or context is ambiguous, return \"status\": \"AMBIGUOUS\".\n");
        sb.append("7. Output ONLY a valid JSON object matching the required structure.\n\n");

        sb.append(schema.getFormattedSchema()).append("\n\n");

        sb.append("REQUIRED JSON STRUCTURE:\n");
        sb.append("{\n");
        sb.append("  \"status\": \"SUPPORTED\" | \"AMBIGUOUS\" | \"UNSUPPORTED\",\n");
        sb.append("  \"statusReason\": \"Optional explanation\",\n");
        sb.append("  \"intent\": \"SELECT\" | \"COUNT\" | \"AGGREGATE\" | \"UNKNOWN\",\n");
        sb.append("  \"targetTables\": [\"table_name\"],\n");
        sb.append("  \"selectFields\": [{\"tableName\": \"table_name\", \"columnName\": \"col_name\", \"alias\": null, \"aggregationType\": \"NONE\"}],\n");
        sb.append("  \"filters\": [{\"tableName\": \"table_name\", \"columnName\": \"col_name\", \"operator\": \"=\", \"value\": \"value\"}],\n");
        sb.append("  \"joins\": [{\"leftTable\": \"t1\", \"leftColumn\": \"c1\", \"rightTable\": \"t2\", \"rightColumn\": \"c2\", \"type\": \"INNER\"}],\n");
        sb.append("  \"orderBy\": [{\"tableName\": \"table_name\", \"columnName\": \"col_name\", \"ascending\": true}],\n");
        sb.append("  \"limit\": 10,\n");
        sb.append("  \"distinct\": false\n");
        sb.append("}\n\n");

        sb.append("User Question: ").append(question);
        return sb.toString();
    }
}
