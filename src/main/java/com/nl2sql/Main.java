package com.nl2sql;

import com.nl2sql.answer.AnswerGenerator;
import com.nl2sql.answer.AnswerResult;
import com.nl2sql.answer.DefaultAnswerGenerator;
import com.nl2sql.client.MockLLMClient;
import com.nl2sql.database.CompanyDatabaseCreator;
import com.nl2sql.database.DatabaseContext;
import com.nl2sql.database.DatabaseInitializer;
import com.nl2sql.database.DatabaseManager;
import com.nl2sql.domain.chemistry.ChemistryKeywordDictionary;
import com.nl2sql.domain.chemistry.ChemistryQueryMapper;
import com.nl2sql.domain.chemistry.ChemistrySchemaInitializer;
import com.nl2sql.domain.chemistry.importer.ChemistryImportRunner;
import com.nl2sql.executor.DatabaseExecutor;
import com.nl2sql.executor.DefaultDatabaseExecutor;
import com.nl2sql.executor.QueryResult;
import com.nl2sql.generator.GeneratedQuery;
import com.nl2sql.generator.SQLQueryGenerator;
import com.nl2sql.metadata.SchemaPrinter;
import com.nl2sql.metadata.SchemaMetadata;
import com.nl2sql.nlp.LLMQuestionParser;
import com.nl2sql.nlp.ParsedQuestion;
import com.nl2sql.nlp.QuestionParser;
import com.nl2sql.nlp.RuleBasedQuestionParser;
import com.nl2sql.pipeline.InteractiveRepl;
import com.nl2sql.pipeline.PipelineResult;
import com.nl2sql.pipeline.UniversalPipeline;
import com.nl2sql.routing.ConnectivityChecker;
import com.nl2sql.routing.HybridQuestionParser;
import com.nl2sql.routing.LLMConfiguration;
import com.nl2sql.routing.ParserMode;
import com.nl2sql.validator.DefaultSQLValidator;
import com.nl2sql.validator.SQLValidationResult;
import com.nl2sql.validator.SQLValidator;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println(" Natural Language -> SQL Pipeline");
        System.out.println(" Phase 18: Universal Pipeline CLI & REPL");
        System.out.println("=================================================");

        if (hasArg(args, "--help") || hasArg(args, "-h")) {
            printHelp();
            return;
        }

        if (hasArg(args, "--import-chemistry")) {
            ChemistryImportRunner.run();
            return;
        }

        if (hasArg(args, "--create-company-db")) {
            try {
                String path = CompanyDatabaseCreator.createCompanyDatabase();
                System.out.println("SUCCESS: Created synthetic company database at " + path);
            } catch (Exception e) {
                System.err.println("ERROR creating synthetic company database: " + e.getMessage());
            }
            return;
        }

        if (hasArg(args, "--benchmark")) {
            boolean offline = hasArg(args, "--offline");
            com.nl2sql.evaluation.BenchmarkReport report = com.nl2sql.evaluation.BenchmarkRunner.run(!offline);
            System.out.println(report.generateSummary());
            return;
        }

        if (hasArg(args, "--web") || hasArg(args, "--gui")) {
            String portStr = parseArg(args, "--port");
            int port = portStr != null ? Integer.parseInt(portStr) : 8080;
            String webDbPath = parseArg(args, "--db");
            if (webDbPath == null) {
                webDbPath = new File("data/company.db").exists() ? "data/company.db" : "data/chemistry.db";
            }
            boolean offline = hasArg(args, "--offline");
            try {
                com.nl2sql.web.WebServer webServer = new com.nl2sql.web.WebServer(port, webDbPath, !offline);
                webServer.start();
                Thread.currentThread().join();
            } catch (Exception e) {
                System.err.println("ERROR starting web dashboard: " + e.getMessage());
            }
            return;
        }

        String inspectPath = parseArg(args, "--inspect-db");
        if (inspectPath == null) {
            inspectPath = parseArg(args, "--inspect");
        }

        if (inspectPath != null) {
            try (DatabaseContext context = DatabaseContext.open(inspectPath)) {
                System.out.println(SchemaPrinter.formatSchemaInspection(context));
            } catch (Exception e) {
                System.err.println("ERROR during schema inspection for " + inspectPath + ": " + e.getMessage());
            }
            return;
        }

        String dbPath = parseArg(args, "--db");
        if (dbPath == null) {
            dbPath = new File("data/company.db").exists() ? "data/company.db" : "data/chemistry.db";
        }

        boolean forceOffline = hasArg(args, "--offline");
        boolean enableOnline = !forceOffline;

        // 1. Single query execution mode
        String query = parseArg(args, "--query");
        if (query == null) {
            query = parseArg(args, "-q");
        }

        if (query != null && !query.trim().isEmpty()) {
            try (DatabaseContext context = DatabaseContext.open(dbPath)) {
                UniversalPipeline pipeline = new UniversalPipeline(context, enableOnline);
                PipelineResult result = pipeline.process(query);
                System.out.println("\n" + result.getFormattedSummary());
            } catch (Exception e) {
                System.err.println("ERROR processing query: " + e.getMessage());
            }
            return;
        }

        // 2. Interactive REPL mode
        if (hasArg(args, "--interactive") || hasArg(args, "--repl") || hasArg(args, "-i")) {
            InteractiveRepl.start(dbPath, enableOnline);
            return;
        }

        // 3. Regression test suite mode
        if (hasArg(args, "--test") || hasArg(args, "--regression")) {
            runBaselineSuite();
            return;
        }

        // If no explicit command, print quick guidance or start interactive REPL
        System.out.println("No specific command passed. Starting Interactive REPL Console...\n");
        InteractiveRepl.start(dbPath, enableOnline);
    }

    private static void printHelp() {
        System.out.println("USAGE:");
        System.out.println("  java -jar natural-language-sql-pipeline.jar [options]");
        System.out.println("\nOPTIONS:");
        System.out.println("  --query, -q <text>      Execute a single natural language question and display results");
        System.out.println("  --interactive, -i       Start the interactive REPL console");
        System.out.println("  --db <path>             Specify SQLite database path (default: data/company.db)");
        System.out.println("  --offline               Force offline rule-based planner (disable Gemini API)");
        System.out.println("  --inspect <path>        Inspect and print catalog schema of a database");
        System.out.println("  --create-company-db     Generate synthetic Company database (data/company.db)");
        System.out.println("  --import-chemistry      Import Chemistry Excel database (data/chemistry.db)");
        System.out.println("  --test, --regression    Run baseline regression suite");
        System.out.println("  --help, -h              Show this help message");
    }

    private static void runBaselineSuite() {
        String defaultDbPath = "data/chemistry.db";
        File dbFile = new File(defaultDbPath);
        if (!dbFile.exists()) {
            defaultDbPath = "data/pipeline_database.db";
        }

        try (DatabaseContext context = DatabaseContext.open(defaultDbPath)) {
            DatabaseManager dbManager = context.getDatabaseManager();
            SchemaMetadata schemaMetadata = context.getSchemaMetadata();

            System.out.println("SUCCESS: Active DatabaseContext opened for " + context.getDbPath());
            System.out.println("SUCCESS: Discovered " + schemaMetadata.getTables().size() + " catalog tables dynamically.");

            if (!schemaMetadata.hasTable("analytes")) {
                DatabaseInitializer initializer = new DatabaseInitializer(dbManager, new ChemistrySchemaInitializer());
                initializer.initializeSchema();
                initializer.seedPlaceholdersIfEmpty();
                context.refreshSchema();
                schemaMetadata = context.getSchemaMetadata();
                System.out.println("SUCCESS: Initialized Chemistry tables into pipeline database.");
            }

            QuestionParser ruleParser = new RuleBasedQuestionParser();
            SQLQueryGenerator generator = new ChemistryQueryMapper();
            SQLValidator validator = new DefaultSQLValidator();
            DatabaseExecutor executor = new DefaultDatabaseExecutor(dbManager);
            AnswerGenerator answerGenerator = new DefaultAnswerGenerator();

            ConnectivityChecker onlineNet = () -> true;
            LLMConfiguration validConfig = new LLMConfiguration("mock-key-123", "gemini-2.5-flash");

            System.out.println("\n=================================================");
            System.out.println(" PHASE 11B & PHASE 14 BASELINE REGRESSION SUITE");
            System.out.println("=================================================");

            int passedCount = 0;
            int totalTests = 8;

            boolean pass1 = schemaMetadata.hasTable("analytes") && schemaMetadata.hasTable("tests") && schemaMetadata.hasTable("reagents");
            passedCount += printTestResult("Test 1: Table Metadata Discovery", pass1);

            ParsedQuestion pq2 = ruleParser.parse("What should I mix for the first anion test?", schemaMetadata);
            GeneratedQuery gq2 = generator.generate(pq2, schemaMetadata);
            boolean pass2 = gq2.isSuccess() && gq2.getSql().contains("reagents") && gq2.getParameters().contains("ANION");
            passedCount += printTestResult("Test 2: ChemistryQueryMapper FIND_REAGENTS query mapping", pass2);

            ParsedQuestion pq3 = ruleParser.parse("What observation should I expect for the confirmatory test of chloride?", schemaMetadata);
            GeneratedQuery gq3 = generator.generate(pq3, schemaMetadata);
            boolean pass3 = gq3.isSuccess() && gq3.getSql().contains("test_observations") && gq3.getParameters().contains("chloride");
            passedCount += printTestResult("Test 3: ChemistryQueryMapper FIND_OBSERVATION query mapping", pass3);

            ParsedQuestion pq4 = ruleParser.parse("What is the procedure for the second test?", schemaMetadata);
            GeneratedQuery gq4 = generator.generate(pq4, schemaMetadata);
            boolean pass4 = gq4.isSuccess() && gq4.getSql().contains("t.procedure_description") && gq4.getParameters().contains(2);
            passedCount += printTestResult("Test 4: ChemistryQueryMapper FIND_PROCEDURE query mapping", pass4);

            boolean pass5 = ChemistryKeywordDictionary.KNOWN_ANALYTES.contains("chloride") && ChemistryKeywordDictionary.REAGENT_KEYWORDS.contains("mix");
            passedCount += printTestResult("Test 5: ChemistryKeywordDictionary vocabulary isolation", pass5);

            SQLValidationResult vr6 = validator.validate(gq2, schemaMetadata);
            QueryResult qr6 = executor.execute(gq2, vr6);
            boolean pass6 = vr6.isValid() && qr6.isSuccess() && qr6.getRowCount() > 0;
            passedCount += printTestResult("Test 6: Generic SQLValidator and DatabaseExecutor integration", pass6);

            AnswerResult ar7 = answerGenerator.generate(pq2, qr6);
            boolean pass7 = ar7.isSuccess();
            passedCount += printTestResult("Test 7: Generic DefaultAnswerGenerator output formatting", pass7);

            String validJson = "{\"intent\": \"FIND_REAGENTS\", \"entities\": [{\"type\": \"sequence_number\", \"value\": 1}, {\"type\": \"analyte_type\", \"value\": \"ANION\"}], \"confidence\": 0.95, \"clarificationRequired\": false}";
            MockLLMClient mockClient = new MockLLMClient(false, validJson);
            LLMQuestionParser llmParser = new LLMQuestionParser(mockClient);
            HybridQuestionParser hybridParser = new HybridQuestionParser(ruleParser, llmParser, onlineNet, validConfig);

            ParsedQuestion res8 = hybridParser.parse("What should I mix for the first anion test?", schemaMetadata);
            GeneratedQuery gq8 = generator.generate(res8, schemaMetadata);
            SQLValidationResult vr8 = validator.validate(gq8, schemaMetadata);
            QueryResult qr8 = executor.execute(gq8, vr8);
            AnswerResult ar8 = answerGenerator.generate(res8, qr8);

            boolean pass8 = hybridParser.getLastMode() == ParserMode.ONLINE_LLM && ar8.isSuccess();
            System.out.println("-------------------------------------------------");
            System.out.println("PHASE 11B END-TO-END PIPELINE RESULT:");
            System.out.println("Routing Mode: " + hybridParser.getLastMode());
            System.out.print(ar8.getFormattedSummary());
            passedCount += printTestResult("Test 8: Full End-to-End Hybrid Pipeline Regression Test", pass8);

            System.out.println("-------------------------------------------------");
            System.out.println("PHASE 11B SUITE SUMMARY: " + passedCount + "/" + totalTests + " TESTS PASSED.");
            System.out.println("=================================================");

            if (passedCount != totalTests) {
                throw new IllegalStateException("Baseline suite failed: " + (totalTests - passedCount) + " test(s) failed.");
            }
        } catch (Exception e) {
            System.err.println("ERROR during main execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String parseArg(String[] args, String flag) {
        for (int i = 0; i < args.length; i++) {
            if (flag.equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
            if (args[i].toLowerCase().startsWith(flag.toLowerCase() + "=")) {
                return args[i].substring((flag + "=").length());
            }
        }
        return null;
    }

    private static boolean hasArg(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase(flag)) return true;
        }
        return false;
    }

    private static int printTestResult(String testName, boolean success) {
        System.out.printf("[%s] %s%n", success ? "PASS" : "FAIL", testName);
        return success ? 1 : 0;
    }
}
