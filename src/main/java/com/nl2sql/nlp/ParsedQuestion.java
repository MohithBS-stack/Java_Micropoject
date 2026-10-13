package com.nl2sql.nlp;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Structured representation of a natural language question after intent and entity extraction.
 * Serves as the intermediate contract between Question Understanding and SQL Generation.
 */
public class ParsedQuestion {
    private final String originalQuestion;
    private final Intent intent;
    private final List<Entity> entities;
    private final double confidence;
    private final boolean clarificationRequired;
    private final String clarificationReason;

    public ParsedQuestion(String originalQuestion, Intent intent, List<Entity> entities,
                          double confidence, boolean clarificationRequired, String clarificationReason) {
        this.originalQuestion = originalQuestion;
        this.intent = intent;
        this.entities = entities != null ? Collections.unmodifiableList(entities) : Collections.emptyList();
        this.confidence = confidence;
        this.clarificationRequired = clarificationRequired;
        this.clarificationReason = clarificationReason;
    }

    public String getOriginalQuestion() {
        return originalQuestion;
    }

    public Intent getIntent() {
        return intent;
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isClarificationRequired() {
        return clarificationRequired;
    }

    public String getClarificationReason() {
        return clarificationReason;
    }

    public String getFormattedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("QUESTION:\n  ").append(originalQuestion).append("\n");
        sb.append("INTENT:\n  ").append(intent).append(" (Confidence: ").append(String.format("%.2f", confidence)).append(")\n");
        sb.append("ENTITIES:\n");
        if (entities.isEmpty()) {
            sb.append("  (None extracted)\n");
        } else {
            for (Entity e : entities) {
                sb.append("  ").append(e.getType()).append(" = ").append(e.getValue()).append("\n");
            }
        }
        sb.append("CLARIFICATION REQUIRED:\n  ").append(clarificationRequired);
        if (clarificationRequired) {
            sb.append(" (Reason: ").append(clarificationReason).append(")");
        }
        sb.append("\n");
        return sb.toString();
    }
}
