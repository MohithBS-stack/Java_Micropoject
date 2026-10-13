package com.nl2sql.pipeline;

import com.nl2sql.database.DatabaseContext;
import com.nl2sql.metadata.SchemaPrinter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Interactive REPL (Read-Eval-Print-Loop) console for the Text-to-SQL pipeline.
 */
public class InteractiveRepl {

    public static void start(String initialDbPath, boolean enableOnline) {
        System.out.println("=============================================================");
        System.out.println("       NATURAL LANGUAGE TO SQL - INTERACTIVE CONSOLE         ");
        System.out.println("=============================================================");
        System.out.println("Type your natural language questions in plain English.");
        System.out.println("Commands:");
        System.out.println("  :schema          - Print discovered database schema");
        System.out.println("  :db <path>       - Switch database context");
        System.out.println("  :mode            - Toggle Online LLM vs Offline Rule-Based");
        System.out.println("  :help            - Show command list");
        System.out.println("  :exit, :quit     - Exit console");
        System.out.println("=============================================================\n");

        String dbPath = (initialDbPath != null && !initialDbPath.isEmpty()) ? initialDbPath : "data/company.db";
        boolean online = enableOnline;
        DatabaseContext context = null;

        try {
            context = DatabaseContext.open(dbPath);
            UniversalPipeline pipeline = new UniversalPipeline(context, online);

            System.out.println("Connected to: " + context.getDbPath() + " (" + context.getSchemaMetadata().getTables().size() + " tables)");
            System.out.println("Engine Mode: " + pipeline.getModeName() + "\n");

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                System.out.print("nl2sql> ");
                String line = reader.readLine();
                if (line == null) break;

                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                if (trimmed.equalsIgnoreCase(":exit") || trimmed.equalsIgnoreCase(":quit")) {
                    System.out.println("Exiting Text-to-SQL console. Goodbye!");
                    break;
                }

                if (trimmed.equalsIgnoreCase(":help")) {
                    System.out.println("Available commands: :schema, :db <path>, :mode, :history, :reset, :help, :exit");
                    continue;
                }

                if (trimmed.equalsIgnoreCase(":reset")) {
                    pipeline.getConversationSession().reset();
                    System.out.println("Conversation history cleared.\n");
                    continue;
                }

                if (trimmed.equalsIgnoreCase(":history")) {
                    List<com.nl2sql.conversation.ConversationTurn> hist = pipeline.getConversationSession().getHistory();
                    if (hist.isEmpty()) {
                        System.out.println("No conversation turns recorded yet.\n");
                    } else {
                        System.out.println("Conversation History (" + hist.size() + " turns):");
                        for (int i = 0; i < hist.size(); i++) {
                            System.out.println("  " + (i + 1) + ". Question: " + hist.get(i).getQuestion());
                        }
                        System.out.println();
                    }
                    continue;
                }

                if (trimmed.equalsIgnoreCase(":schema")) {
                    System.out.println(SchemaPrinter.formatSchemaInspection(context));
                    continue;
                }

                if (trimmed.startsWith(":db ")) {
                    String newPath = trimmed.substring(4).trim();
                    try {
                        DatabaseContext newContext = DatabaseContext.open(newPath);
                        context.close();
                        context = newContext;
                        pipeline = new UniversalPipeline(context, online);
                        System.out.println("Switched to database: " + newPath + " (" + context.getSchemaMetadata().getTables().size() + " tables)\n");
                    } catch (Exception ex) {
                        System.err.println("Error opening database " + newPath + ": " + ex.getMessage() + "\n");
                    }
                    continue;
                }

                if (trimmed.equalsIgnoreCase(":mode")) {
                    online = !online;
                    pipeline = new UniversalPipeline(context, online);
                    System.out.println("Toggled engine mode to: " + pipeline.getModeName() + "\n");
                    continue;
                }

                // Process natural language question
                try {
                    PipelineResult result = pipeline.process(trimmed);
                    System.out.println("\n" + result.getFormattedSummary() + "\n");
                } catch (Exception e) {
                    System.err.println("Pipeline Execution Error: " + e.getMessage() + "\n");
                }
            }

        } catch (Exception e) {
            System.err.println("Fatal REPL error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (context != null) {
                try { context.close(); } catch (Exception ignored) {}
            }
        }
    }
}
