package com.nl2sql.routing;

import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.nlp.LLMQuestionParser;
import com.nl2sql.nlp.ParsedQuestion;
import com.nl2sql.nlp.QuestionParser;
import com.nl2sql.nlp.RuleBasedQuestionParser;

/**
 * Hybrid Question Parser Router.
 * Automatically routes questions to online LLM parser when internet and API keys are available,
 * with guaranteed non-blocking fallback to offline RuleBasedQuestionParser on any network/API failure.
 */
public class HybridQuestionParser implements QuestionParser {

    private final QuestionParser offlineParser;
    private final QuestionParser onlineParser;
    private final ConnectivityChecker connectivityChecker;
    private final LLMConfiguration llmConfiguration;

    private ParserMode lastMode = ParserMode.OFFLINE_RULE_BASED;
    private String lastFallbackReason = null;

    public HybridQuestionParser(ConnectivityChecker connectivityChecker, LLMConfiguration llmConfiguration) {
        this(new RuleBasedQuestionParser(), new LLMQuestionParser(llmConfiguration), connectivityChecker, llmConfiguration);
    }

    public HybridQuestionParser(QuestionParser offlineParser, QuestionParser onlineParser,
                                ConnectivityChecker connectivityChecker, LLMConfiguration llmConfiguration) {
        this.offlineParser = offlineParser;
        this.onlineParser = onlineParser;
        this.connectivityChecker = connectivityChecker;
        this.llmConfiguration = llmConfiguration;
    }

    @Override
    public ParsedQuestion parse(String question, SchemaMetadata schemaMetadata) {
        lastFallbackReason = null;

        // Step 1: Check Internet Connectivity
        if (connectivityChecker == null || !connectivityChecker.isInternetAvailable()) {
            lastMode = ParserMode.OFFLINE_RULE_BASED;
            lastFallbackReason = "Internet connectivity is unavailable";
            return offlineParser.parse(question, schemaMetadata);
        }

        // Step 2: Check LLM API Key / Configuration
        if (llmConfiguration == null || !llmConfiguration.isConfigured()) {
            lastMode = ParserMode.OFFLINE_RULE_BASED;
            lastFallbackReason = "LLM API configuration or API key is missing";
            return offlineParser.parse(question, schemaMetadata);
        }

        // Step 3: Attempt Online LLM Question Parsing with Graceful Fallback
        try {
            ParsedQuestion onlineResult = onlineParser.parse(question, schemaMetadata);
            if (isValidParsedQuestion(onlineResult)) {
                lastMode = ParserMode.ONLINE_LLM;
                return onlineResult;
            } else {
                lastFallbackReason = "LLM response failed safety validation checks";
            }
        } catch (Exception e) {
            lastFallbackReason = "LLM API call failed: " + e.getMessage();
        }

        // Step 4: Fallback to Offline Rule-Based Parser
        lastMode = ParserMode.OFFLINE_RULE_BASED;
        return offlineParser.parse(question, schemaMetadata);
    }

    /**
     * Validates that ParsedQuestion object produced by online LLM is structurally sound.
     */
    private boolean isValidParsedQuestion(ParsedQuestion result) {
        if (result == null) return false;
        if (result.getIntent() == null) return false;
        if (result.getConfidence() < 0.0 || result.getConfidence() > 1.0) return false;
        return result.getEntities() != null;
    }

    public ParserMode getLastMode() {
        return lastMode;
    }

    public String getLastFallbackReason() {
        return lastFallbackReason;
    }
}
