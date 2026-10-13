package com.nl2sql.nlp;

import com.nl2sql.domain.chemistry.ChemistryKeywordDictionary;
import com.nl2sql.metadata.SchemaMetadata;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic rule-based implementation of QuestionParser.
 * Uses ChemistryKeywordDictionary for domain keyword definitions while maintaining generic NLP parsing control flow.
 */
public class RuleBasedQuestionParser implements QuestionParser {

    @Override
    public ParsedQuestion parse(String question, SchemaMetadata schemaMetadata) {
        if (question == null || question.trim().isEmpty()) {
            return new ParsedQuestion(question, Intent.UNKNOWN, Collections.emptyList(), 0.0, true, "Empty question");
        }

        String normalized = normalize(question);
        List<Entity> entities = extractEntities(normalized);
        Intent intent = determineIntent(normalized, entities);

        // Ambiguity Check & Clarification Assessment
        boolean clarificationRequired = false;
        String clarificationReason = null;
        double confidence = 0.9;

        if (intent == Intent.UNKNOWN) {
            clarificationRequired = true;
            clarificationReason = "Unrecognized question intent or domain topic";
            confidence = 0.0;
        } else if (requiresEntitySpecification(intent, normalized) && entities.isEmpty()) {
            clarificationRequired = true;
            clarificationReason = "Question is ambiguous: missing target analyte, test type, or sequence specification";
            confidence = 0.4;
        }

        return new ParsedQuestion(question, intent, entities, confidence, clarificationRequired, clarificationReason);
    }

    private String normalize(String text) {
        String lower = text.toLowerCase();
        String clean = lower.replaceAll("[^a-z0-9\\s]", " ");
        return clean.replaceAll("\\s+", " ").trim();
    }

    private Intent determineIntent(String normalized, List<Entity> entities) {
        if (containsAny(normalized, "how many", "count", "number of", "total count")) {
            return Intent.COUNT_RECORDS;
        }
        if (containsAnyList(normalized, ChemistryKeywordDictionary.REAGENT_KEYWORDS)) {
            return Intent.FIND_REAGENTS;
        }
        if (containsAnyList(normalized, ChemistryKeywordDictionary.OBSERVATION_KEYWORDS)) {
            return Intent.FIND_OBSERVATION;
        }
        if (containsAnyList(normalized, ChemistryKeywordDictionary.PROCEDURE_KEYWORDS)) {
            return Intent.FIND_PROCEDURE;
        }
        if (containsAnyList(normalized, ChemistryKeywordDictionary.TEST_KEYWORDS)) {
            return Intent.FIND_TEST;
        }

        return Intent.UNKNOWN;
    }

    private List<Entity> extractEntities(String normalized) {
        List<Entity> entities = new ArrayList<>();

        // 1. Ordinal / Sequence Number Extraction via ChemistryKeywordDictionary
        for (Map.Entry<String, Integer> entry : ChemistryKeywordDictionary.ORDINAL_MAP.entrySet()) {
            if (containsWord(normalized, entry.getKey())) {
                entities.add(new Entity("sequence_number", entry.getValue(), entry.getKey()));
                break;
            }
        }

        if (entities.stream().noneMatch(e -> e.getType().equals("sequence_number"))) {
            Pattern numPattern = Pattern.compile("\\b(?:test|step|number)\\s+(\\d+)\\b");
            Matcher matcher = numPattern.matcher(normalized);
            if (matcher.find()) {
                int seq = Integer.parseInt(matcher.group(1));
                entities.add(new Entity("sequence_number", seq, matcher.group(0)));
            }
        }

        // 2. Analyte Type Extraction (ANION / CATION)
        if (containsWord(normalized, "anion") || containsWord(normalized, "anions")) {
            entities.add(new Entity("analyte_type", "ANION", "anion"));
        } else if (containsWord(normalized, "cation") || containsWord(normalized, "cations")) {
            entities.add(new Entity("analyte_type", "CATION", "cation"));
        }

        // 3. Test Type Extraction (PRELIMINARY / CONFIRMATORY)
        if (containsWord(normalized, "preliminary")) {
            entities.add(new Entity("test_type", "prelim", "preliminary"));
        } else if (containsWord(normalized, "confirmatory") || containsWord(normalized, "positive test")) {
            entities.add(new Entity("test_type", "confirm", "confirmatory"));
        }

        // 4. Reagent Entity Extraction
        String textForAnalyte = normalized;
        List<String> knownReagents = List.of("silver nitrate", "barium chloride", "sulfuric acid", "hydrochloric acid", "nitric acid", "sodium hydroxide", "ammonia");
        for (String reagent : knownReagents) {
            if (normalized.contains(reagent)) {
                entities.add(new Entity("reagent", reagent, reagent));
                textForAnalyte = textForAnalyte.replace(reagent, " ");
                break;
            }
        }

        // 5. Specific Analyte Subject Extraction
        boolean analyteFound = false;
        for (String item : ChemistryKeywordDictionary.KNOWN_ANALYTES) {
            if (containsWord(textForAnalyte, item)) {
                entities.add(new Entity("analyte", item, item));
                analyteFound = true;
                break;
            }
        }

        if (!analyteFound) {
            Pattern targetPattern = Pattern.compile("\\b(?:test for|test of|for|identify)\\s+([a-z0-9]+)\\b");
            Matcher matcher = targetPattern.matcher(textForAnalyte);
            if (matcher.find()) {
                String candidate = matcher.group(1);
                if (!List.of("the", "a", "an", "all", "which").contains(candidate)) {
                    entities.add(new Entity("analyte", candidate, candidate));
                }
            }
        }

        return entities;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyList(String text, List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private boolean requiresEntitySpecification(Intent intent, String normalized) {
        if (normalized.contains("all") || normalized.contains("available") || normalized.contains("list all")) {
            return false;
        }
        return intent == Intent.FIND_REAGENTS || intent == Intent.FIND_OBSERVATION
                || intent == Intent.FIND_PROCEDURE || intent == Intent.FIND_TEST;
    }
}
