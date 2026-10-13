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
import com.nl2sql.validator.DefaultSQLValidator;
import com.nl2sql.validator.SQLValidationResult;
import com.nl2sql.validator.SQLValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional Test Suite for the 10 Chemistry Questions using data/chemistry.db.
 */
public class ChemistryFunctionalSuiteTest {

    private static final String CHEM_DB_PATH = "data/chemistry.db";
    private static DatabaseManager dbManager;
    private static SchemaMetadata schemaMetadata;
    private static QuestionParser ruleParser;
    private static QuestionParser hybridParser;
    private static SQLQueryGenerator queryMapper;
    private static SQLValidator validator;
    private static DatabaseExecutor executor;
    private static AnswerGenerator answerGenerator;
    private static String parserModeName;

    @BeforeAll
    public static void setUp() throws Exception {
        File dbFile = new File(CHEM_DB_PATH);
        assertTrue(dbFile.exists(), "data/chemistry.db must exist for functional test suite");

        dbManager = new DatabaseManager(CHEM_DB_PATH);
        SchemaInspector inspector = new SchemaInspector(dbManager);
        schemaMetadata = inspector.inspectSchema();

        ruleParser = new RuleBasedQuestionParser();
        queryMapper = new ChemistryQueryMapper();
        validator = new DefaultSQLValidator();
        executor = new DefaultDatabaseExecutor(dbManager);
        answerGenerator = new DefaultAnswerGenerator();

        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            ConnectivityChecker onlineNet = () -> true;
            LLMConfiguration config = new LLMConfiguration(apiKey, "gemini-2.5-flash");
            LLMQuestionParser llmParser = new LLMQuestionParser(config);
            hybridParser = new HybridQuestionParser(ruleParser, llmParser, onlineNet, config);
            parserModeName = "HybridQuestionParser (ONLINE_LLM)";
        } else {
            hybridParser = ruleParser;
            parserModeName = "RuleBasedQuestionParser (OFFLINE_FALLBACK)";
        }
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    @DisplayName("Execute 10 End-to-End Functional Test Questions on data/chemistry.db")
    public void executeTenQuestionsSuite() {
        String[] questions = {
                "What reagents are needed to test for chloride?",
                "How do I test for sulfate?",
                "What is the positive test for nitrate?",
                "How many tests are available?",
                "What reagent is used to test for unobtanium?",
                "What reagent should I use?",
                "Who won the 2024 World Cup?",
                "I want to identify chloride. Which reagents should I add?",
                "Which tests use silver nitrate?",
                "List all the available reagents."
        };

        int passed = 0;
        int failed = 0;

        for (int i = 0; i < questions.length; i++) {
            int testNum = i + 1;
            String questionText = questions[i];

            System.out.println("==================================================");
            System.out.println("TEST " + testNum);
            System.out.println("QUESTION:\n" + questionText + "\n");

            ParsedQuestion parsed = hybridParser.parse(questionText, schemaMetadata);
            System.out.println("PARSER MODE:\n" + parserModeName + "\n");
            System.out.println("PARSED QUESTION:\nOriginal Question: " + parsed.getOriginalQuestion());
            System.out.println("Intent: " + parsed.getIntent());
            System.out.println("Entities: " + parsed.getEntities());
            System.out.println("Clarification Required: " + parsed.isClarificationRequired()
                    + (parsed.getClarificationReason() != null ? " (" + parsed.getClarificationReason() + ")" : "")
                    + "\n");

            GeneratedQuery query = queryMapper.generate(parsed, schemaMetadata);
            System.out.println("GENERATED SQL:\n"
                    + (query.getSql() != null ? query.getSql() : "(NONE - SQL Generation Refused)") + "\n");
            System.out.println("PARAMETERS:\n" + query.getParameters() + "\n");

            SQLValidationResult validationResult = null;
            if (query.isSuccess() && query.getSql() != null) {
                validationResult = validator.validate(query, schemaMetadata);
                System.out.println("VALIDATION:\nValid: " + validationResult.isValid() + " ("
                        + validationResult.getReason() + ")\n");
            } else {
                System.out.println("VALIDATION:\n(SKIPPED - Query Not Generated: " + query.getErrorMessage() + ")\n");
            }

            QueryResult queryResult = null;
            if (validationResult != null && validationResult.isValid()) {
                queryResult = executor.execute(query, validationResult);
                System.out.println("ROWS:\nRow Count: " + queryResult.getRowCount());
                System.out.println("Columns: " + queryResult.getColumnNames());
                if (queryResult.getRowCount() > 0) {
                    System.out.println("First Row: " + queryResult.getRows().get(0) + "\n");
                } else {
                    System.out.println("Data: (0 rows returned)\n");
                }
            } else {
                System.out.println("ROWS:\n(SKIPPED - Query Execution Not Applicable)\n");
            }

            AnswerResult answerResult = answerGenerator.generate(parsed, queryResult);
            System.out.println("FINAL ANSWER:\n" + answerResult.getAnswerText() + "\n");

            boolean testPassed = evaluateTestSuccess(testNum, parsed, query, validationResult, queryResult,
                    answerResult);
            if (testPassed) {
                passed++;
                System.out.println("STATUS:\nPASS");
            } else {
                failed++;
                System.out.println("STATUS:\nFAIL");
            }
            System.out.println("==================================================\n");
        }

        System.out.println("TOTAL TESTS: " + questions.length);
        System.out.println("PASSED: " + passed);
        System.out.println("FAILED: " + failed);

        assertEquals(0, failed, "All 10 questions must pass in the functional suite");
    }

    private boolean evaluateTestSuccess(int testNum, ParsedQuestion parsed, GeneratedQuery query,
            SQLValidationResult validation, QueryResult result, AnswerResult answer) {
        switch (testNum) {
            case 1: // Chloride reagents
            case 8: // Identify chloride reagents
                return parsed.getIntent() == Intent.FIND_REAGENTS
                        && query.isSuccess()
                        && validation != null && validation.isValid()
                        && result != null && result.getRowCount() > 0
                        && answer.getAnswerText().contains("Dilute nitric acid");

            case 2: // Sulfate procedure
                return parsed.getIntent() == Intent.FIND_PROCEDURE
                        && query.isSuccess()
                        && validation != null && validation.isValid()
                        && result != null && result.getRowCount() > 0
                        && answer.getAnswerText().contains("barium chloride");

            case 3: // Nitrate positive test
                return (parsed.getIntent() == Intent.FIND_TEST || parsed.getIntent() == Intent.FIND_OBSERVATION)
                        && query.isSuccess()
                        && validation != null && validation.isValid()
                        && result != null && result.getRowCount() > 0;

            case 4: // Count tests
                return parsed.getIntent() == Intent.COUNT_RECORDS
                        && query.isSuccess()
                        && validation != null && validation.isValid()
                        && result != null && result.getRowCount() == 1
                        && ((Number) result.getRows().get(0).get(0)).longValue() > 0;

            case 5: // Unobtanium anti-hallucination
                return parsed.getIntent() == Intent.FIND_REAGENTS
                        && query.isSuccess()
                        && validation != null && validation.isValid()
                        && result != null && result.getRowCount() == 0
                        && answer.getAnswerText().contains("No matching records");

            case 6: // Ambiguous reagent query
                return parsed.isClarificationRequired()
                        && !query.isSuccess()
                        && query.getSql() == null;

            case 7: // Out of domain
                return parsed.getIntent() == Intent.UNKNOWN
                        && parsed.isClarificationRequired()
                        && !query.isSuccess()
                        && query.getSql() == null;

            case 9: // Tests using silver nitrate
                return parsed.getIntent() == Intent.FIND_TEST
                        && query.isSuccess()
                        && validation != null && validation.isValid()
                        && result != null && result.getRowCount() > 0
                        && answer.getAnswerText().contains("Silver nitrate");

            case 10: // List all reagents
                return parsed.getIntent() == Intent.FIND_REAGENTS
                        && !parsed.isClarificationRequired()
                        && query.isSuccess()
                        && validation != null && validation.isValid()
                        && result != null && result.getRowCount() > 10;

            default:
                return false;
        }
    }
}
