package com.nl2sql.pipeline;

import com.nl2sql.answer.AnswerResult;
import com.nl2sql.answer.UniversalAnswerGenerator;
import com.nl2sql.database.DatabaseContext;
import com.nl2sql.executor.DatabaseExecutor;
import com.nl2sql.executor.DefaultDatabaseExecutor;
import com.nl2sql.executor.QueryResult;
import com.nl2sql.generator.GenericQueryPlanSQLGenerator;
import com.nl2sql.generator.GeneratedQuery;
import com.nl2sql.generator.QueryPlanSQLGenerator;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.plan.*;
import com.nl2sql.routing.LLMConfiguration;
import com.nl2sql.validator.DefaultSQLValidator;
import com.nl2sql.validator.SQLValidationResult;
import com.nl2sql.validator.SQLValidator;

/**
 * Universal end-to-end pipeline connecting natural-language questions to database execution.
 * Orchestrates:
 * 1. QuestionUnderstandingEngine (Online Gemini with automatic offline fallback, or offline rule-based)
 * 2. QueryPlanSQLGenerator (AST to Parameterized SQL)
 * 3. SQLValidator (Injection protection, catalog schema compliance)
 * 4. DatabaseExecutor (Safe parameterized JDBC execution)
 * 5. UniversalAnswerGenerator (Result set presentation and synthesis)
 */
public class UniversalPipeline {

    private final DatabaseContext databaseContext;
    private final QuestionUnderstandingEngine planner;
    private final QueryPlanSQLGenerator generator;
    private final SQLValidator validator;
    private final DatabaseExecutor executor;
    private final UniversalAnswerGenerator answerGenerator;
    private final com.nl2sql.conversation.ConversationSession conversationSession;
    private final String modeName;

    public UniversalPipeline(DatabaseContext databaseContext) {
        this(databaseContext, true);
    }

    public UniversalPipeline(DatabaseContext databaseContext, boolean enableOnlineLLM) {
        this.databaseContext = databaseContext;
        this.generator = new GenericQueryPlanSQLGenerator();
        this.validator = new DefaultSQLValidator();
        this.executor = new DefaultDatabaseExecutor(databaseContext.getDatabaseManager());
        this.answerGenerator = new UniversalAnswerGenerator();
        this.conversationSession = new com.nl2sql.conversation.ConversationSession();

        QuestionUnderstandingEngine basePlanner;
        if (enableOnlineLLM) {
            LLMConfiguration config = new LLMConfiguration();
            basePlanner = new LLMQueryPlanner(config);
            this.modeName = config.isConfigured() ? "ONLINE (Gemini + Offline Fallback)" : "OFFLINE (Rule-Based)";
        } else {
            basePlanner = new RuleBasedQueryPlanner();
            this.modeName = "OFFLINE (Rule-Based)";
        }
        this.planner = new com.nl2sql.conversation.ConversationalQueryPlanner(basePlanner, this.conversationSession);
    }

    public UniversalPipeline(DatabaseContext databaseContext,
                             QuestionUnderstandingEngine planner,
                             QueryPlanSQLGenerator generator,
                             SQLValidator validator,
                             DatabaseExecutor executor,
                             UniversalAnswerGenerator answerGenerator,
                             String modeName) {
        this(databaseContext, planner, generator, validator, executor, answerGenerator, new com.nl2sql.conversation.ConversationSession(), modeName);
    }

    public UniversalPipeline(DatabaseContext databaseContext,
                             QuestionUnderstandingEngine planner,
                             QueryPlanSQLGenerator generator,
                             SQLValidator validator,
                             DatabaseExecutor executor,
                             UniversalAnswerGenerator answerGenerator,
                             com.nl2sql.conversation.ConversationSession conversationSession,
                             String modeName) {
        this.databaseContext = databaseContext;
        this.conversationSession = conversationSession != null ? conversationSession : new com.nl2sql.conversation.ConversationSession();
        this.planner = (planner instanceof com.nl2sql.conversation.ConversationalQueryPlanner)
                ? planner
                : new com.nl2sql.conversation.ConversationalQueryPlanner(planner, this.conversationSession);
        this.generator = generator;
        this.validator = validator;
        this.executor = executor;
        this.answerGenerator = answerGenerator;
        this.modeName = modeName;
    }

    public PipelineResult process(String question) {
        long startTime = System.currentTimeMillis();
        SchemaMetadata schema = databaseContext.getSchemaMetadata();

        // 1. Understand natural language question into QueryPlan
        QueryPlan plan = planner.understand(question, schema);

        if (plan.getStatus() != QueryPlanStatus.SUPPORTED) {
            long duration = System.currentTimeMillis() - startTime;
            AnswerResult refusalAnswer = answerGenerator.generate(plan, null);
            return new PipelineResult(question, plan, null, null, null, refusalAnswer, duration, modeName);
        }

        // 2. Generate parameterized SQL from QueryPlan
        GeneratedQuery query = generator.generate(plan, schema);
        if (!query.isSuccess()) {
            long duration = System.currentTimeMillis() - startTime;
            AnswerResult genFailAnswer = AnswerResult.failure("SQL Generation Error: " + query.getErrorMessage());
            return new PipelineResult(question, plan, query, null, null, genFailAnswer, duration, modeName);
        }

        // 3. Validate generated SQL against schema and security rules
        SQLValidationResult validation = validator.validate(query, schema);
        if (!validation.isValid()) {
            long duration = System.currentTimeMillis() - startTime;
            AnswerResult valFailAnswer = AnswerResult.failure("Security/Schema Validation Failed: " + validation.getReason());
            return new PipelineResult(question, plan, query, validation, null, valFailAnswer, duration, modeName);
        }

        // 4. Execute query safely with parameter binding
        QueryResult queryResult = executor.execute(query, validation);

        // 5. Synthesize answer
        AnswerResult answer = answerGenerator.generate(plan, queryResult);
        long duration = System.currentTimeMillis() - startTime;

        if (queryResult != null && queryResult.isSuccess()) {
            conversationSession.recordTurn(question, plan, queryResult);
        }

        return new PipelineResult(question, plan, query, validation, queryResult, answer, duration, modeName);
    }

    public DatabaseContext getDatabaseContext() { return databaseContext; }
    public String getModeName() { return modeName; }
    public com.nl2sql.conversation.ConversationSession getConversationSession() { return conversationSession; }
}
