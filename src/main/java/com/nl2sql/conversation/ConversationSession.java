package com.nl2sql.conversation;

import com.nl2sql.executor.QueryResult;
import com.nl2sql.plan.QueryPlan;
import com.nl2sql.plan.QueryPlanStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Manages conversational state across multiple user interactions.
 * Tracks conversation history and detects follow-up questions referencing previous queries.
 */
public class ConversationSession {

    private final List<ConversationTurn> history = new ArrayList<>();
    private static final Pattern FOLLOW_UP_PATTERN = Pattern.compile(
            "(?i)\\b(their|them|among them|those|these|only those|and also|filter by|sort by|order by|what about|how many of them|average of that|highest of them)\\b"
    );

    public synchronized void recordTurn(String question, QueryPlan plan, QueryResult result) {
        if (plan != null && plan.getStatus() == QueryPlanStatus.SUPPORTED) {
            history.add(new ConversationTurn(question, plan, result));
        }
    }

    public synchronized QueryPlan getLastSupportedPlan() {
        for (int i = history.size() - 1; i >= 0; i--) {
            QueryPlan p = history.get(i).getPlan();
            if (p != null && p.getStatus() == QueryPlanStatus.SUPPORTED) {
                return p;
            }
        }
        return null;
    }

    public synchronized boolean isFollowUp(String question) {
        if (history.isEmpty() || question == null) {
            return false;
        }
        String trimmed = question.trim();
        // Check for explicit follow-up tokens
        if (FOLLOW_UP_PATTERN.matcher(trimmed).find()) {
            return true;
        }
        // Short clauses starting with "where", "with", "ordered by", "sorted by"
        if (trimmed.matches("(?i)^(where|with|and|or|order by|sort by|limit)\\s+.*")) {
            return true;
        }
        return false;
    }

    public synchronized List<ConversationTurn> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized void reset() {
        history.clear();
    }

    public synchronized int getTurnCount() {
        return history.size();
    }
}
