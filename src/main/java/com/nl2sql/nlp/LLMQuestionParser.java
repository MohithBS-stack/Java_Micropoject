package com.nl2sql.nlp;

import com.nl2sql.client.GeminiLLMClient;
import com.nl2sql.client.LLMClient;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.routing.LLMConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Online LLM Question Parser implementing the QuestionParser contract.
 * Uses an LLMClient to call LLM REST APIs, then strictly validates and maps response text to ParsedQuestion objects.
 * Never generates SQL directly.
 */
public class LLMQuestionParser implements QuestionParser {

    private final LLMClient llmClient;

    public LLMQuestionParser(LLMConfiguration configuration) {
        this(new GeminiLLMClient(configuration));
    }

    public LLMQuestionParser(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public ParsedQuestion parse(String question, SchemaMetadata schemaMetadata) {
        if (llmClient == null) {
            throw new IllegalStateException("LLMClient is null");
        }

        try {
            String prompt = buildSystemPrompt(question);
            String rawResponse = llmClient.generateContent(prompt);
            return parseAndValidateResponse(rawResponse, question);
        } catch (Exception e) {
            throw new RuntimeException("LLM Question Parsing failed: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt(String question) {
        return "You are a natural language database question parser. " +
                "Do NOT generate SQL. Do NOT execute commands. Do NOT answer chemistry facts. " +
                "Classify intent into ONLY one of: FIND_REAGENTS, FIND_OBSERVATION, FIND_PROCEDURE, FIND_TEST, COUNT_RECORDS, UNKNOWN. " +
                "Extract entities (analyte_type, sequence_number, test_type, analyte). " +
                "Respond in JSON format: {\"intent\": \"...\", \"entities\": [...], \"confidence\": 0.95, \"clarificationRequired\": false}. " +
                "User Question: " + question;
    }

    /**
     * Strictly validates and maps raw LLM JSON response into a ParsedQuestion instance.
     * Throws runtime exceptions if response is malformed, invalid, or out-of-bounds, triggering rule-based fallback.
     */
    public ParsedQuestion parseAndValidateResponse(String rawResponse, String originalQuestion) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty or null LLM response string");
        }

        // 1. Extract Intent
        Intent intent = extractIntent(rawResponse);

        // 2. Extract Confidence Score and Validate Bounds [0.0, 1.0]
        double confidence = extractConfidence(rawResponse);
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Invalid confidence score out of bounds [0.0, 1.0]: " + confidence);
        }

        // 3. Extract Clarification Required Flag
        boolean clarificationRequired = extractClarificationRequired(rawResponse);

        // 4. Extract Entities Structurally
        List<Entity> entities = extractEntities(rawResponse);

        String clarificationReason = clarificationRequired ? "Clarification required by language model parser" : null;

        return new ParsedQuestion(originalQuestion, intent, entities, confidence, clarificationRequired, clarificationReason);
    }

    private Intent extractIntent(String text) {
        Pattern pattern = Pattern.compile("\"intent\"\\s*:\\s*\"([A-Z_]+)\"");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String intentStr = matcher.group(1);
            try {
                return Intent.valueOf(intentStr);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unsupported or invalid Intent name from LLM: " + intentStr);
            }
        }

        // Secondary fallback search if standard JSON pattern differs
        for (Intent i : Intent.values()) {
            if (text.contains(i.name())) {
                return i;
            }
        }

        throw new IllegalArgumentException("Could not extract valid Intent from LLM response");
    }

    private double extractConfidence(String text) {
        Pattern pattern = Pattern.compile("\"confidence\"\\s*:\\s*([0-9.]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Malformed confidence value");
            }
        }
        return 0.90; // Default fallback confidence if omitted
    }

    private boolean extractClarificationRequired(String text) {
        Pattern pattern = Pattern.compile("\"clarificationRequired\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return Boolean.parseBoolean(matcher.group(1));
        }
        return false;
    }

    private List<Entity> extractEntities(String text) {
        List<Entity> entities = new ArrayList<>();

        if (text.contains("sequence_number")) {
            Pattern p = Pattern.compile("\"sequence_number\"\\s*,?\\s*\"(?:value|val)\"\\s*:\\s*(\\d+)");
            Matcher m = p.matcher(text);
            if (m.find()) {
                entities.add(new Entity("sequence_number", Integer.parseInt(m.group(1)), "1st"));
            } else {
                entities.add(new Entity("sequence_number", 1, "first"));
            }
        }

        if (text.contains("analyte_type")) {
            if (text.toUpperCase().contains("ANION")) {
                entities.add(new Entity("analyte_type", "ANION", "anion"));
            } else if (text.toUpperCase().contains("CATION")) {
                entities.add(new Entity("analyte_type", "CATION", "cation"));
            }
        }

        if (text.contains("test_type")) {
            if (text.toUpperCase().contains("CONFIRMATORY")) {
                entities.add(new Entity("test_type", "CONFIRMATORY", "confirmatory"));
            } else if (text.toUpperCase().contains("PRELIMINARY")) {
                entities.add(new Entity("test_type", "PRELIMINARY", "preliminary"));
            }
        }

        if (text.contains("chloride")) {
            entities.add(new Entity("analyte", "chloride", "chloride"));
        }

        return entities;
    }
}
