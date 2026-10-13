package com.nl2sql.conversation;

import com.nl2sql.database.DatabaseContext;
import com.nl2sql.pipeline.PipelineResult;
import com.nl2sql.pipeline.UniversalPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 21: Conversational Memory & Multi-Turn Context.
 */
public class ConversationalPipelineTest {

    private static final String COMPANY_DB_PATH = Paths.get("data", "company.db").toAbsolutePath().toString();

    @Test
    @DisplayName("1. Multi-turn context: initial query followed by aggregation follow-up")
    public void testFollowUpAggregation() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            UniversalPipeline pipeline = new UniversalPipeline(context, false);

            // Turn 1: Baseline query
            PipelineResult res1 = pipeline.process("List all employees");
            assertTrue(res1.isSuccess());
            assertEquals(4, res1.getQueryResult().getRowCount());
            assertEquals(1, pipeline.getConversationSession().getTurnCount());

            // Turn 2: Follow-up asking for average salary of them
            PipelineResult res2 = pipeline.process("What is their average salary?");
            assertTrue(res2.isSuccess());
            assertTrue(res2.getGeneratedQuery().getSql().contains("AVG("));
            assertTrue(res2.getGeneratedQuery().getSql().contains("salary"));
            assertEquals(2, pipeline.getConversationSession().getTurnCount());
        }
    }

    @Test
    @DisplayName("2. Multi-turn context: ordering follow-up")
    public void testFollowUpOrdering() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            UniversalPipeline pipeline = new UniversalPipeline(context, false);

            // Turn 1
            PipelineResult res1 = pipeline.process("Show employee names");
            assertTrue(res1.isSuccess());

            // Turn 2: Follow-up sorting them by salary descending
            PipelineResult res2 = pipeline.process("Sort them by salary descending");
            assertTrue(res2.isSuccess());
            assertTrue(res2.getGeneratedQuery().getSql().contains("ORDER BY"));
            assertTrue(res2.getGeneratedQuery().getSql().contains("DESC"));
        }
    }

    @Test
    @DisplayName("3. Session reset clears conversation history")
    public void testSessionReset() throws Exception {
        try (DatabaseContext context = DatabaseContext.open(COMPANY_DB_PATH)) {
            UniversalPipeline pipeline = new UniversalPipeline(context, false);

            pipeline.process("List all employees");
            assertEquals(1, pipeline.getConversationSession().getTurnCount());

            pipeline.getConversationSession().reset();
            assertEquals(0, pipeline.getConversationSession().getTurnCount());
            assertNull(pipeline.getConversationSession().getLastSupportedPlan());
        }
    }
}
