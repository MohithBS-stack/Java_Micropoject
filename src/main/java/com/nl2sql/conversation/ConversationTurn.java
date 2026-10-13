package com.nl2sql.conversation;

import com.nl2sql.executor.QueryResult;
import com.nl2sql.plan.QueryPlan;

/**
 * Encapsulates a single turn in a multi-turn conversation.
 */
public class ConversationTurn {

    private final String question;
    private final QueryPlan plan;
    private final QueryResult result;
    private final long timestamp;

    public ConversationTurn(String question, QueryPlan plan, QueryResult result) {
        this.question = question;
        this.plan = plan;
        this.result = result;
        this.timestamp = System.currentTimeMillis();
    }

    public String getQuestion() { return question; }
    public QueryPlan getPlan() { return plan; }
    public QueryResult getResult() { return result; }
    public long getTimestamp() { return timestamp; }
}
