package com.nl2sql.answer;

import com.nl2sql.client.MockLLMClient;
import com.nl2sql.executor.QueryResult;
import com.nl2sql.plan.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 20: Natural-Language Answer Synthesis.
 */
public class UniversalAnswerGeneratorTest {

    private final UniversalAnswerGenerator generator = new UniversalAnswerGenerator();

    @Test
    @DisplayName("1. COUNT intent produces conversational count sentence")
    public void testCountSentenceSynthesis() {
        QueryPlan plan = new QueryPlan.Builder("How many employees are there?")
                .status(QueryPlanStatus.SUPPORTED)
                .intent(QueryIntent.COUNT)
                .addTargetTable("employees")
                .build();

        QueryResult result = QueryResult.success(
                Collections.singletonList("total_count"),
                Collections.singletonList(Collections.singletonList((Object) 4))
        );

        AnswerResult answer = generator.generate(plan, result);
        assertTrue(answer.isSuccess());
        assertEquals("Found a total of 4 employees records in the database.", answer.getAnswerText());
        assertEquals(1, answer.getRowCount());
    }

    @Test
    @DisplayName("2. Aggregation query produces conversational summary")
    public void testAggregationSummarySynthesis() {
        QueryPlan plan = new QueryPlan.Builder("What is the average salary in engineering?")
                .status(QueryPlanStatus.SUPPORTED)
                .intent(QueryIntent.SELECT)
                .addTargetTable("employees")
                .addSelectField(new SelectField("employees", "salary", "avg_salary", AggregationType.AVG))
                .build();

        QueryResult result = QueryResult.success(
                Collections.singletonList("avg_salary"),
                Collections.singletonList(Collections.singletonList((Object) 88500.0))
        );

        AnswerResult answer = generator.generate(plan, result);
        assertTrue(answer.isSuccess());
        assertTrue(answer.getAnswerText().contains("Calculated summary for employees"));
        assertTrue(answer.getAnswerText().contains("88500.0"));
    }

    @Test
    @DisplayName("3. Single entity record produces clean bullet points")
    public void testSingleEntityRecord() {
        QueryPlan plan = new QueryPlan.Builder("Find Alice Smith")
                .status(QueryPlanStatus.SUPPORTED)
                .intent(QueryIntent.SELECT)
                .addTargetTable("employees")
                .build();

        QueryResult result = QueryResult.success(
                Arrays.asList("id", "name", "salary"),
                Collections.singletonList(Arrays.asList((Object) 101, "Alice Smith", 85000.0))
        );

        AnswerResult answer = generator.generate(plan, result);
        assertTrue(answer.isSuccess());
        assertTrue(answer.getAnswerText().contains("Found 1 record in employees"));
        assertTrue(answer.getAnswerText().contains("Alice Smith"));
    }

    @Test
    @DisplayName("4. Zero rows found produces clear factual message")
    public void testZeroRows() {
        QueryPlan plan = new QueryPlan.Builder("Employees named Zachary")
                .status(QueryPlanStatus.SUPPORTED)
                .intent(QueryIntent.SELECT)
                .addTargetTable("employees")
                .build();

        List<List<Object>> emptyRows = Collections.emptyList();
        QueryResult result = QueryResult.success(
                Arrays.asList("id", "name"),
                emptyRows
        );

        AnswerResult answer = generator.generate(plan, result);
        assertTrue(answer.isSuccess());
        assertEquals(0, answer.getRowCount());
        assertTrue(answer.getAnswerText().contains("No matching records found"));
    }

    @Test
    @DisplayName("5. LLM Answer Synthesis with MockLLMClient")
    public void testLLMAnswerSynthesis() {
        String mockSummary = "There are 4 registered employees at the firm with salaries between $65k and $110k.";
        MockLLMClient mockClient = new MockLLMClient(false, mockSummary);
        UniversalAnswerGenerator llmGenerator = new UniversalAnswerGenerator(mockClient);

        QueryPlan plan = new QueryPlan.Builder("Overview of employees")
                .status(QueryPlanStatus.SUPPORTED)
                .intent(QueryIntent.SELECT)
                .addTargetTable("employees")
                .build();

        QueryResult result = QueryResult.success(
                Arrays.asList("id", "name"),
                Arrays.asList(
                        Arrays.asList((Object) 101, "Alice"),
                        Arrays.asList((Object) 102, "Bob")
                )
        );

        AnswerResult answer = llmGenerator.generate(plan, result);
        assertTrue(answer.isSuccess());
        assertEquals(mockSummary, answer.getAnswerText());
    }
}
