package com.nl2sql.domain.chemistry;

import com.nl2sql.answer.AnswerGenerator;
import com.nl2sql.answer.AnswerResult;
import com.nl2sql.answer.DefaultAnswerGenerator;
import com.nl2sql.database.DatabaseManager;
import com.nl2sql.executor.DatabaseExecutor;
import com.nl2sql.executor.DefaultDatabaseExecutor;
import com.nl2sql.executor.QueryResult;
import com.nl2sql.generator.GeneratedQuery;
import com.nl2sql.generator.SQLQueryGenerator;
import com.nl2sql.metadata.SchemaInspector;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.nlp.Intent;
import com.nl2sql.nlp.LLMQuestionParser;
import com.nl2sql.nlp.ParsedQuestion;
import com.nl2sql.nlp.QuestionParser;
import com.nl2sql.nlp.RuleBasedQuestionParser;
import com.nl2sql.routing.ConnectivityChecker;
import com.nl2sql.routing.HybridQuestionParser;
import com.nl2sql.routing.LLMConfiguration;
import com.nl2sql.routing.ParserMode;
import com.nl2sql.validator.DefaultSQLValidator;
import com.nl2sql.validator.SQLValidationResult;
import com.nl2sql.validator.SQLValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 13: End-to-End Real Chemistry Query Verification Test Suite.
 * Validates the natural-language to SQL pipeline against the real imported data/chemistry.db database.
 */
public class ChemistryEndToEndTest {

    private static final String CHEM_DB_PATH = "data/chemistry.db";
    private static DatabaseManager dbManager;
    private static SchemaMetadata schemaMetadata;
    private static QuestionParser ruleParser;
    private static SQLQueryGenerator queryMapper;
    private static SQLValidator validator;
    private static DatabaseExecutor executor;
    private static AnswerGenerator answerGenerator;

    @BeforeAll
    public static void setUp() throws Exception {
        File dbFile = new File(CHEM_DB_PATH);
        assertTrue(dbFile.exists(), "data/chemistry.db must exist for Phase 13 verification tests");

        dbManager = new DatabaseManager(CHEM_DB_PATH);
        SchemaInspector inspector = new SchemaInspector(dbManager);
        schemaMetadata = inspector.inspectSchema();

        ruleParser = new RuleBasedQuestionParser();
        queryMapper = new ChemistryQueryMapper();
        validator = new DefaultSQLValidator();
        executor = new DefaultDatabaseExecutor(dbManager);
        answerGenerator = new DefaultAnswerGenerator();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    @DisplayName("1. Schema Inspection - Verify real chemistry.db tables discovered")
    public void testSchemaInspection() {
        assertNotNull(schemaMetadata);
        assertTrue(schemaMetadata.hasTable("analytes"));
        assertTrue(schemaMetadata.hasTable("tests"));
        assertTrue(schemaMetadata.hasTable("reagents"));
        assertTrue(schemaMetadata.hasTable("test_reagents"));
        assertTrue(schemaMetadata.hasTable("salt_tests"));
        assertTrue(schemaMetadata.hasTable("salts"));
        assertTrue(schemaMetadata.hasTable("reactions"));
        assertTrue(schemaMetadata.hasTable("safety_rules"));
    }

    @Test
    @DisplayName("2. Reagent Lookup Query - End-to-End Real Data Verification for Chloride")
    public void testReagentLookupForChloride() {
        String question = "What reagents are needed to test for chloride?";
        ParsedQuestion parsed = ruleParser.parse(question, schemaMetadata);

        assertEquals(Intent.FIND_REAGENTS, parsed.getIntent());
        assertFalse(parsed.isClarificationRequired());

        GeneratedQuery query = queryMapper.generate(parsed, schemaMetadata);
        assertTrue(query.isSuccess());
        assertTrue(query.getSql().toUpperCase().startsWith("SELECT"));
        assertTrue(query.getSql().contains("reagents"));

        SQLValidationResult validation = validator.validate(query, schemaMetadata);
        assertTrue(validation.isValid(), "Generated SQL must pass SQLValidator");

        QueryResult queryResult = executor.execute(query, validation);
        assertTrue(queryResult.isSuccess());
        assertTrue(queryResult.getRowCount() > 0, "Query must return real rows from chemistry.db");

        AnswerResult answerResult = answerGenerator.generate(parsed, queryResult);
        assertTrue(answerResult.isSuccess());
        String text = answerResult.getAnswerText();
        assertTrue(text.contains("Silver nitrate") || text.contains("nitric acid"), "Answer must contain real reagent name from DB");
    }

    @Test
    @DisplayName("3. Procedure Lookup Query - End-to-End Real Data Verification for Sulfate")
    public void testProcedureLookupForSulfate() {
        String question = "What is the procedure to test for sulfate?";
        ParsedQuestion parsed = ruleParser.parse(question, schemaMetadata);

        assertEquals(Intent.FIND_PROCEDURE, parsed.getIntent());
        assertFalse(parsed.isClarificationRequired());

        GeneratedQuery query = queryMapper.generate(parsed, schemaMetadata);
        assertTrue(query.isSuccess());

        SQLValidationResult validation = validator.validate(query, schemaMetadata);
        assertTrue(validation.isValid());

        QueryResult queryResult = executor.execute(query, validation);
        assertTrue(queryResult.isSuccess());
        assertEquals(1, queryResult.getRowCount());

        AnswerResult answerResult = answerGenerator.generate(parsed, queryResult);
        assertTrue(answerResult.isSuccess());
        assertTrue(answerResult.getAnswerText().contains("barium chloride"), "Procedure description must contain real DB content");
    }

    @Test
    @DisplayName("4. Expected Observation Query - End-to-End Real Data Verification for Nitrate")
    public void testObservationLookupForNitrate() {
        String question = "What is the expected observation for nitrate test?";
        ParsedQuestion parsed = ruleParser.parse(question, schemaMetadata);

        assertEquals(Intent.FIND_OBSERVATION, parsed.getIntent());
        assertFalse(parsed.isClarificationRequired());

        GeneratedQuery query = queryMapper.generate(parsed, schemaMetadata);
        assertTrue(query.isSuccess());

        SQLValidationResult validation = validator.validate(query, schemaMetadata);
        assertTrue(validation.isValid());

        QueryResult queryResult = executor.execute(query, validation);
        assertTrue(queryResult.isSuccess());
        assertTrue(queryResult.getRowCount() > 0);

        AnswerResult answerResult = answerGenerator.generate(parsed, queryResult);
        assertTrue(answerResult.isSuccess());
        assertTrue(answerResult.getAnswerText().toLowerCase().contains("brown ring"), "Observation result must report brown ring from DB");
    }

    @Test
    @DisplayName("5. Count Query - Verify record counting on real chemistry.db")
    public void testCountRecords() {
        String question = "How many tests are in the database?";
        ParsedQuestion parsed = ruleParser.parse(question, schemaMetadata);

        assertEquals(Intent.COUNT_RECORDS, parsed.getIntent());

        GeneratedQuery query = queryMapper.generate(parsed, schemaMetadata);
        assertTrue(query.isSuccess());

        SQLValidationResult validation = validator.validate(query, schemaMetadata);
        assertTrue(validation.isValid());

        QueryResult queryResult = executor.execute(query, validation);
        assertTrue(queryResult.isSuccess());
        assertEquals(1, queryResult.getRowCount());
        assertTrue(((Number) queryResult.getRows().get(0).get(0)).longValue() > 0);
    }

    @Test
    @DisplayName("6. Anti-Hallucination - Non-existent analyte does not invent answers")
    public void testAntiHallucinationNonExistentAnalyte() {
        // Query for a non-existent analyte entity ("unobtanium")
        ParsedQuestion parsed = new ParsedQuestion(
                "What reagents are needed to test for unobtanium?",
                Intent.FIND_REAGENTS,
                java.util.List.of(new com.nl2sql.nlp.Entity("analyte", "unobtanium", "unobtanium")),
                0.95,
                false,
                null
        );

        GeneratedQuery query = queryMapper.generate(parsed, schemaMetadata);
        assertTrue(query.isSuccess());

        SQLValidationResult validation = validator.validate(query, schemaMetadata);
        assertTrue(validation.isValid());

        QueryResult result = executor.execute(query, validation);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getRowCount(), "Non-existent analyte must return 0 rows from database");

        AnswerResult answerResult = answerGenerator.generate(parsed, result);
        assertTrue(answerResult.isSuccess());
        assertTrue(answerResult.getAnswerText().contains("No matching records"), "Pipeline must report no matching records");
    }

    @Test
    @DisplayName("7. Ambiguous Question - Requires clarification and refuses SQL generation")
    public void testAmbiguousQuestionRequiresClarification() {
        String question = "What reagent should I use?";
        ParsedQuestion parsed = ruleParser.parse(question, schemaMetadata);

        assertTrue(parsed.isClarificationRequired(), "Ambiguous question must require clarification");
        assertNotNull(parsed.getClarificationReason());

        GeneratedQuery query = queryMapper.generate(parsed, schemaMetadata);
        assertFalse(query.isSuccess(), "Mapper must refuse SQL generation when clarification is required");
    }

    @Test
    @DisplayName("8. Out-of-Domain Question - Returns UNKNOWN intent and refuses SQL generation")
    public void testOutOfDomainQuestion() {
        String question = "Who won the 2024 World Cup?";
        ParsedQuestion parsed = ruleParser.parse(question, schemaMetadata);

        assertEquals(Intent.UNKNOWN, parsed.getIntent());
        assertTrue(parsed.isClarificationRequired());

        GeneratedQuery query = queryMapper.generate(parsed, schemaMetadata);
        assertFalse(query.isSuccess());
        assertEquals(Intent.UNKNOWN, query.getIntent());
    }

    @Test
    @DisplayName("9. SQL Safety - Verify SELECT-only, parameterization, and no stack/DML injection")
    public void testSQLSafety() {
        String question = "What reagents are needed to test for chloride?";
        ParsedQuestion parsed = ruleParser.parse(question, schemaMetadata);
        GeneratedQuery query = queryMapper.generate(parsed, schemaMetadata);

        String sql = query.getSql().toUpperCase();
        assertTrue(sql.startsWith("SELECT"));
        assertFalse(sql.contains(";"));
        assertFalse(sql.contains("INSERT"));
        assertFalse(sql.contains("UPDATE"));
        assertFalse(sql.contains("DELETE"));
        assertFalse(sql.contains("DROP"));
        assertFalse(sql.contains("ALTER"));
        assertFalse(query.getParameters().isEmpty(), "Parameters must be parameterized");
    }

    @Test
    @DisplayName("10. Online Mode Routing Test (Conditional on GEMINI_API_KEY)")
    public void testOnlineModeIfConfigured() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println("SKIPPED: Online-mode test skipped (GEMINI_API_KEY environment variable not set)");
            return;
        }

        ConnectivityChecker onlineChecker = () -> true;
        LLMConfiguration config = new LLMConfiguration(apiKey, "gemini-2.5-flash");
        LLMQuestionParser llmParser = new LLMQuestionParser(config);
        HybridQuestionParser hybridParser = new HybridQuestionParser(ruleParser, llmParser, onlineChecker, config);

        ParsedQuestion parsed = hybridParser.parse("What reagents are needed to test for chloride?", schemaMetadata);
        assertEquals(ParserMode.ONLINE_LLM, hybridParser.getLastMode());
        assertFalse(parsed.isClarificationRequired());

        GeneratedQuery query = queryMapper.generate(parsed, schemaMetadata);
        SQLValidationResult validation = validator.validate(query, schemaMetadata);
        assertTrue(validation.isValid());

        QueryResult result = executor.execute(query, validation);
        assertTrue(result.getRowCount() > 0);
    }
}
