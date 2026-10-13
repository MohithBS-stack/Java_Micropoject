package com.nl2sql.web;

import com.nl2sql.database.DatabaseContext;
import com.nl2sql.evaluation.BenchmarkReport;
import com.nl2sql.evaluation.BenchmarkRunner;
import com.nl2sql.metadata.ColumnMetadata;
import com.nl2sql.metadata.TableMetadata;
import com.nl2sql.pipeline.PipelineResult;
import com.nl2sql.pipeline.UniversalPipeline;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Embedded lightweight HTTP Web Server providing a rich Web GUI and REST APIs
 * for interactive natural language queries, schema visualization, and evaluation benchmarks.
 */
public class WebServer {

    private final int port;
    private HttpServer server;
    private String currentDbPath;
    private boolean onlineMode;

    public WebServer(int port, String initialDbPath, boolean onlineMode) {
        this.port = port;
        this.currentDbPath = initialDbPath != null ? initialDbPath : "data/company.db";
        this.onlineMode = onlineMode;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", new StaticPageHandler());
        server.createContext("/api/query", new QueryApiHandler());
        server.createContext("/api/schema", new SchemaApiHandler());
        server.createContext("/api/benchmark", new BenchmarkApiHandler());

        server.setExecutor(null); // default executor
        server.start();

        System.out.println("=============================================================");
        System.out.println("  Universal Text-to-SQL Web Server Started!");
        System.out.println("  URL: http://localhost:" + port + "/");
        System.out.println("  Active Database: " + currentDbPath);
        System.out.println("  Engine Mode: " + (onlineMode ? "ONLINE (Gemini + Fallback)" : "OFFLINE (Rule-Based)"));
        System.out.println("=============================================================\n");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private class StaticPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] htmlBytes = getIndexHtml().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, htmlBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(htmlBytes);
            }
        }
    }

    private class QueryApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String question = extractJsonString(body, "question");
            String db = extractJsonString(body, "database");
            String mode = extractJsonString(body, "mode");

            if (db != null && !db.trim().isEmpty()) {
                currentDbPath = db.trim();
            }
            boolean useOnline = (mode != null && mode.equalsIgnoreCase("online"));

            if (question == null || question.trim().isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"Question is required\"}");
                return;
            }

            try (DatabaseContext context = DatabaseContext.open(currentDbPath)) {
                UniversalPipeline pipeline = new UniversalPipeline(context, useOnline);
                PipelineResult result = pipeline.process(question);

                StringBuilder json = new StringBuilder();
                json.append("{\n");
                json.append("  \"success\": ").append(result.isSuccess()).append(",\n");
                json.append("  \"question\": \"").append(escapeJson(question)).append("\",\n");
                json.append("  \"database\": \"").append(escapeJson(currentDbPath)).append("\",\n");
                json.append("  \"mode\": \"").append(escapeJson(result.getPlannerMode())).append("\",\n");
                json.append("  \"executionTimeMs\": ").append(result.getExecutionTimeMs()).append(",\n");

                String sql = (result.getGeneratedQuery() != null) ? result.getGeneratedQuery().getSql() : "";
                json.append("  \"sql\": \"").append(escapeJson(sql)).append("\",\n");

                String answerText = (result.getAnswerResult() != null) ? result.getAnswerResult().getAnswerText() : "";
                json.append("  \"answer\": \"").append(escapeJson(answerText)).append("\",\n");

                json.append("  \"columns\": [");
                if (result.getQueryResult() != null) {
                    List<String> cols = result.getQueryResult().getColumnNames();
                    for (int i = 0; i < cols.size(); i++) {
                        json.append("\"").append(escapeJson(cols.get(i))).append("\"");
                        if (i < cols.size() - 1) json.append(", ");
                    }
                }
                json.append("],\n");

                json.append("  \"rows\": [");
                if (result.getQueryResult() != null) {
                    List<List<Object>> rows = result.getQueryResult().getRows();
                    for (int r = 0; r < rows.size(); r++) {
                        json.append("[");
                        List<Object> row = rows.get(r);
                        for (int c = 0; c < row.size(); c++) {
                            Object val = row.get(c);
                            if (val == null) {
                                json.append("null");
                            } else if (val instanceof Number || val instanceof Boolean) {
                                json.append(val);
                            } else {
                                json.append("\"").append(escapeJson(val.toString())).append("\"");
                            }
                            if (c < row.size() - 1) json.append(", ");
                        }
                        json.append("]");
                        if (r < rows.size() - 1) json.append(", ");
                    }
                }
                json.append("]\n");
                json.append("}");

                sendJsonResponse(exchange, 200, json.toString());

            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class SchemaApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (DatabaseContext context = DatabaseContext.open(currentDbPath)) {
                StringBuilder json = new StringBuilder();
                json.append("{\n");
                json.append("  \"database\": \"").append(escapeJson(currentDbPath)).append("\",\n");
                json.append("  \"tables\": [\n");

                int tIdx = 0;
                var tables = context.getSchemaMetadata().getTables().values();
                for (TableMetadata table : tables) {
                    json.append("    {\n");
                    json.append("      \"name\": \"").append(escapeJson(table.getTableName())).append("\",\n");
                    json.append("      \"columns\": [");
                    for (int c = 0; c < table.getColumns().size(); c++) {
                        ColumnMetadata col = table.getColumns().get(c);
                        json.append("{\"name\":\"").append(escapeJson(col.getName()))
                            .append("\",\"type\":\"").append(escapeJson(col.getDataType()))
                            .append("\",\"pk\":").append(col.isPrimaryKey()).append("}");
                        if (c < table.getColumns().size() - 1) json.append(", ");
                    }
                    json.append("]\n");
                    json.append("    }");
                    if (++tIdx < tables.size()) json.append(",");
                    json.append("\n");
                }
                json.append("  ]\n");
                json.append("}");

                sendJsonResponse(exchange, 200, json.toString());
            } catch (Exception ex) {
                sendJsonResponse(exchange, 500, "{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class BenchmarkApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            BenchmarkReport report = BenchmarkRunner.run(false);
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"total\": ").append(report.getTotalCount()).append(",\n");
            json.append("  \"passed\": ").append(report.getPassedCount()).append(",\n");
            json.append("  \"failed\": ").append(report.getFailedCount()).append(",\n");
            json.append("  \"accuracy\": ").append(String.format("%.1f", report.getAccuracyRate())).append(",\n");
            json.append("  \"cases\": [\n");

            for (int i = 0; i < report.getResults().size(); i++) {
                BenchmarkReport.CaseResult cr = report.getResults().get(i);
                json.append("    {");
                json.append("\"id\":\"").append(escapeJson(cr.benchmarkCase.getId())).append("\", ");
                json.append("\"category\":\"").append(escapeJson(cr.benchmarkCase.getCategory())).append("\", ");
                json.append("\"question\":\"").append(escapeJson(cr.benchmarkCase.getQuestion())).append("\", ");
                json.append("\"passed\":").append(cr.passed).append(", ");
                json.append("\"durationMs\":").append(cr.durationMs);
                json.append("}");
                if (i < report.getResults().size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ]\n");
            json.append("}");

            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String extractJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String getIndexHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Natural Language to SQL Universal Engine</title>\n" +
                "  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">\n" +
                "  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>\n" +
                "  <link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap\" rel=\"stylesheet\">\n" +
                "  <style>\n" +
                "    :root {\n" +
                "      --bg: #090d16;\n" +
                "      --surface: #111827;\n" +
                "      --surface-border: #1f293d;\n" +
                "      --primary: #3b82f6;\n" +
                "      --primary-hover: #2563eb;\n" +
                "      --accent: #10b981;\n" +
                "      --text: #f3f4f6;\n" +
                "      --text-muted: #9ca3af;\n" +
                "      --code-bg: #0d1117;\n" +
                "    }\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
                "    body {\n" +
                "      font-family: 'Plus Jakarta Sans', sans-serif;\n" +
                "      background-color: var(--bg);\n" +
                "      color: var(--text);\n" +
                "      min-height: 100vh;\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "    }\n" +
                "    header {\n" +
                "      background: rgba(17, 24, 39, 0.8);\n" +
                "      backdrop-filter: blur(12px);\n" +
                "      border-bottom: 1px solid var(--surface-border);\n" +
                "      padding: 1rem 2rem;\n" +
                "      display: flex;\n" +
                "      justify-content: space-between;\n" +
                "      align-items: center;\n" +
                "      position: sticky;\n" +
                "      top: 0;\n" +
                "      z-index: 50;\n" +
                "    }\n" +
                "    .logo-container {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 0.75rem;\n" +
                "    }\n" +
                "    .badge-icon {\n" +
                "      width: 36px;\n" +
                "      height: 36px;\n" +
                "      background: linear-gradient(135deg, #3b82f6, #8b5cf6);\n" +
                "      border-radius: 8px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      font-weight: 800;\n" +
                "      color: white;\n" +
                "    }\n" +
                "    .title-text h1 {\n" +
                "      font-size: 1.15rem;\n" +
                "      font-weight: 700;\n" +
                "      letter-spacing: -0.02em;\n" +
                "    }\n" +
                "    .title-text p {\n" +
                "      font-size: 0.75rem;\n" +
                "      color: var(--text-muted);\n" +
                "    }\n" +
                "    .header-controls {\n" +
                "      display: flex;\n" +
                "      gap: 1rem;\n" +
                "      align-items: center;\n" +
                "    }\n" +
                "    select, button, input {\n" +
                "      font-family: inherit;\n" +
                "    }\n" +
                "    .ctrl-select {\n" +
                "      background: var(--surface);\n" +
                "      color: var(--text);\n" +
                "      border: 1px solid var(--surface-border);\n" +
                "      padding: 0.5rem 0.85rem;\n" +
                "      border-radius: 8px;\n" +
                "      font-size: 0.85rem;\n" +
                "      outline: none;\n" +
                "    }\n" +
                "    .main-layout {\n" +
                "      display: grid;\n" +
                "      grid-template-columns: 280px 1fr;\n" +
                "      flex: 1;\n" +
                "      overflow: hidden;\n" +
                "    }\n" +
                "    aside {\n" +
                "      border-right: 1px solid var(--surface-border);\n" +
                "      background: rgba(17, 24, 39, 0.4);\n" +
                "      padding: 1.5rem 1rem;\n" +
                "      overflow-y: auto;\n" +
                "    }\n" +
                "    .schema-heading {\n" +
                "      font-size: 0.75rem;\n" +
                "      text-transform: uppercase;\n" +
                "      letter-spacing: 0.08em;\n" +
                "      color: var(--text-muted);\n" +
                "      font-weight: 700;\n" +
                "      margin-bottom: 1rem;\n" +
                "    }\n" +
                "    .table-card {\n" +
                "      background: var(--surface);\n" +
                "      border: 1px solid var(--surface-border);\n" +
                "      border-radius: 8px;\n" +
                "      margin-bottom: 0.75rem;\n" +
                "      padding: 0.75rem;\n" +
                "    }\n" +
                "    .table-name {\n" +
                "      font-weight: 600;\n" +
                "      font-size: 0.85rem;\n" +
                "      color: #60a5fa;\n" +
                "      margin-bottom: 0.4rem;\n" +
                "    }\n" +
                "    .col-list {\n" +
                "      font-family: 'JetBrains Mono', monospace;\n" +
                "      font-size: 0.7rem;\n" +
                "      color: var(--text-muted);\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      gap: 0.2rem;\n" +
                "    }\n" +
                "    main {\n" +
                "      padding: 2rem;\n" +
                "      overflow-y: auto;\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      gap: 1.5rem;\n" +
                "    }\n" +
                "    .query-box-card {\n" +
                "      background: var(--surface);\n" +
                "      border: 1px solid var(--surface-border);\n" +
                "      border-radius: 12px;\n" +
                "      padding: 1.5rem;\n" +
                "      box-shadow: 0 4px 20px rgba(0,0,0,0.3);\n" +
                "    }\n" +
                "    .input-row {\n" +
                "      display: flex;\n" +
                "      gap: 0.75rem;\n" +
                "      margin-top: 0.75rem;\n" +
                "    }\n" +
                "    .query-input {\n" +
                "      flex: 1;\n" +
                "      background: var(--code-bg);\n" +
                "      border: 1px solid var(--surface-border);\n" +
                "      color: var(--text);\n" +
                "      padding: 0.85rem 1rem;\n" +
                "      border-radius: 8px;\n" +
                "      font-size: 0.95rem;\n" +
                "      outline: none;\n" +
                "      transition: border 0.2s;\n" +
                "    }\n" +
                "    .query-input:focus {\n" +
                "      border-color: var(--primary);\n" +
                "    }\n" +
                "    .btn-submit {\n" +
                "      background: linear-gradient(135deg, #2563eb, #3b82f6);\n" +
                "      color: white;\n" +
                "      border: none;\n" +
                "      padding: 0 1.5rem;\n" +
                "      border-radius: 8px;\n" +
                "      font-weight: 600;\n" +
                "      cursor: pointer;\n" +
                "      transition: transform 0.1s, opacity 0.2s;\n" +
                "    }\n" +
                "    .btn-submit:hover {\n" +
                "      opacity: 0.9;\n" +
                "      transform: translateY(-1px);\n" +
                "    }\n" +
                "    .quick-chips {\n" +
                "      display: flex;\n" +
                "      gap: 0.5rem;\n" +
                "      margin-top: 0.75rem;\n" +
                "      flex-wrap: wrap;\n" +
                "    }\n" +
                "    .chip {\n" +
                "      background: rgba(59, 130, 246, 0.1);\n" +
                "      color: #93c5fd;\n" +
                "      border: 1px solid rgba(59, 130, 246, 0.2);\n" +
                "      font-size: 0.75rem;\n" +
                "      padding: 0.3rem 0.65rem;\n" +
                "      border-radius: 20px;\n" +
                "      cursor: pointer;\n" +
                "      transition: all 0.2s;\n" +
                "    }\n" +
                "    .chip:hover {\n" +
                "      background: rgba(59, 130, 246, 0.25);\n" +
                "    }\n" +
                "    .result-card {\n" +
                "      background: var(--surface);\n" +
                "      border: 1px solid var(--surface-border);\n" +
                "      border-radius: 12px;\n" +
                "      padding: 1.5rem;\n" +
                "      display: none;\n" +
                "    }\n" +
                "    .result-header {\n" +
                "      display: flex;\n" +
                "      justify-content: space-between;\n" +
                "      align-items: center;\n" +
                "      margin-bottom: 1rem;\n" +
                "    }\n" +
                "    .meta-tag {\n" +
                "      background: rgba(16, 185, 129, 0.1);\n" +
                "      color: var(--accent);\n" +
                "      font-size: 0.75rem;\n" +
                "      padding: 0.25rem 0.6rem;\n" +
                "      border-radius: 6px;\n" +
                "      font-family: 'JetBrains Mono', monospace;\n" +
                "    }\n" +
                "    .answer-summary {\n" +
                "      background: rgba(59, 130, 246, 0.08);\n" +
                "      border-left: 4px solid var(--primary);\n" +
                "      padding: 1rem;\n" +
                "      border-radius: 0 8px 8px 0;\n" +
                "      font-size: 1rem;\n" +
                "      line-height: 1.5;\n" +
                "      margin-bottom: 1rem;\n" +
                "    }\n" +
                "    .sql-box {\n" +
                "      background: var(--code-bg);\n" +
                "      border: 1px solid var(--surface-border);\n" +
                "      border-radius: 8px;\n" +
                "      padding: 1rem;\n" +
                "      font-family: 'JetBrains Mono', monospace;\n" +
                "      font-size: 0.85rem;\n" +
                "      color: #38bdf8;\n" +
                "      margin-bottom: 1rem;\n" +
                "      overflow-x: auto;\n" +
                "    }\n" +
                "    table {\n" +
                "      width: 100%;\n" +
                "      border-collapse: collapse;\n" +
                "      font-size: 0.85rem;\n" +
                "      margin-top: 1rem;\n" +
                "    }\n" +
                "    th, td {\n" +
                "      padding: 0.75rem 1rem;\n" +
                "      text-align: left;\n" +
                "      border-bottom: 1px solid var(--surface-border);\n" +
                "    }\n" +
                "    th {\n" +
                "      background: rgba(255,255,255,0.02);\n" +
                "      color: var(--text-muted);\n" +
                "      font-weight: 600;\n" +
                "    }\n" +
                "    tr:hover td {\n" +
                "      background: rgba(255,255,255,0.02);\n" +
                "    }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <header>\n" +
                "    <div class=\"logo-container\">\n" +
                "      <div class=\"badge-icon\">SQL</div>\n" +
                "      <div class=\"title-text\">\n" +
                "        <h1>Universal Text-to-SQL Pipeline</h1>\n" +
                "        <p>Phase 24: Enterprise Multi-Domain Dashboard</p>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "    <div class=\"header-controls\">\n" +
                "      <select id=\"dbSelect\" class=\"ctrl-select\" onchange=\"switchDatabase()\">\n" +
                "        <option value=\"data/company.db\">Company Database (company.db)</option>\n" +
                "        <option value=\"data/chemistry.db\">Chemistry Laboratory (chemistry.db)</option>\n" +
                "      </select>\n" +
                "      <select id=\"modeSelect\" class=\"ctrl-select\">\n" +
                "        <option value=\"offline\">Offline Rule-Based</option>\n" +
                "        <option value=\"online\">Online Gemini + Fallback</option>\n" +
                "      </select>\n" +
                "    </div>\n" +
                "  </header>\n" +
                "  <div class=\"main-layout\">\n" +
                "    <aside id=\"schemaSidebar\">\n" +
                "      <div class=\"schema-heading\">Catalog Schema</div>\n" +
                "      <div id=\"schemaCards\">Loading schema...</div>\n" +
                "    </aside>\n" +
                "    <main>\n" +
                "      <div class=\"query-box-card\">\n" +
                "        <h2>Ask a Natural Language Question</h2>\n" +
                "        <div class=\"input-row\">\n" +
                "          <input id=\"queryInput\" class=\"query-input\" type=\"text\" placeholder=\"e.g. List all employees with salary greater than 70000\" onkeydown=\"if(event.key==='Enter') executeQuery()\" />\n" +
                "          <button class=\"btn-submit\" onclick=\"executeQuery()\">Execute</button>\n" +
                "        </div>\n" +
                "        <div class=\"quick-chips\" id=\"quickChips\">\n" +
                "          <!-- Populated dynamically -->\n" +
                "        </div>\n" +
                "      </div>\n" +
                "      <div id=\"resultCard\" class=\"result-card\">\n" +
                "        <div class=\"result-header\">\n" +
                "          <h3>Generated Response</h3>\n" +
                "          <span id=\"metaDuration\" class=\"meta-tag\">0ms</span>\n" +
                "        </div>\n" +
                "        <div id=\"answerSummary\" class=\"answer-summary\"></div>\n" +
                "        <div class=\"schema-heading\">Validated Parameterized SQL</div>\n" +
                "        <pre id=\"sqlCode\" class=\"sql-box\"></pre>\n" +
                "        <div class=\"schema-heading\">Database Result Set</div>\n" +
                "        <div style=\"overflow-x:auto;\">\n" +
                "          <table id=\"resultTable\">\n" +
                "            <thead><tr id=\"tableHeaders\"></tr></thead>\n" +
                "            <tbody id=\"tableBody\"></tbody>\n" +
                "          </table>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "    </main>\n" +
                "  </div>\n" +
                "  <script>\n" +
                "    async function loadSchema() {\n" +
                "      try {\n" +
                "        const res = await fetch('/api/schema');\n" +
                "        const data = await res.json();\n" +
                "        const container = document.getElementById('schemaCards');\n" +
                "        container.innerHTML = '';\n" +
                "        data.tables.forEach(t => {\n" +
                "          const card = document.createElement('div');\n" +
                "          card.className = 'table-card';\n" +
                "          card.innerHTML = `<div class=\"table-name\">${t.name}</div><div class=\"col-list\">${t.columns.map(c => `• ${c.name} (${c.type})`).join('')}</div>`;\n" +
                "          container.appendChild(card);\n" +
                "        });\n" +
                "        updateQuickChips();\n" +
                "      } catch (err) { console.error(err); }\n" +
                "    }\n" +
                "    function updateQuickChips() {\n" +
                "      const db = document.getElementById('dbSelect').value;\n" +
                "      const chips = document.getElementById('quickChips');\n" +
                "      chips.innerHTML = '';\n" +
                "      const queries = db.includes('company')\n" +
                "        ? ['List all employees', 'Show employee names', 'Show employees with salary greater than 50000', 'How many employees are there?', 'Show average salary of employees', 'Show employees in the Engineering department']\n" +
                "        : ['List all reagents', 'How many tests are there?', 'List all analytes', 'List all salts', 'List all reactions'];\n" +
                "      queries.forEach(q => {\n" +
                "        const c = document.createElement('div');\n" +
                "        c.className = 'chip';\n" +
                "        c.innerText = q;\n" +
                "        c.onclick = () => { document.getElementById('queryInput').value = q; executeQuery(); };\n" +
                "        chips.appendChild(c);\n" +
                "      });\n" +
                "    }\n" +
                "    function switchDatabase() {\n" +
                "      const db = document.getElementById('dbSelect').value;\n" +
                "      fetch('/api/query', { method: 'POST', body: JSON.stringify({ question: 'switch', database: db }) }).finally(() => loadSchema());\n" +
                "    }\n" +
                "    async function executeQuery() {\n" +
                "      const q = document.getElementById('queryInput').value.trim();\n" +
                "      if (!q) return;\n" +
                "      const db = document.getElementById('dbSelect').value;\n" +
                "      const mode = document.getElementById('modeSelect').value;\n" +
                "      const resCard = document.getElementById('resultCard');\n" +
                "      try {\n" +
                "        const res = await fetch('/api/query', { method: 'POST', body: JSON.stringify({ question: q, database: db, mode: mode }) });\n" +
                "        const data = await res.json();\n" +
                "        resCard.style.display = 'block';\n" +
                "        document.getElementById('metaDuration').innerText = data.executionTimeMs + 'ms (' + data.mode + ')';\n" +
                "        document.getElementById('answerSummary').innerText = data.answer || 'Query executed.';\n" +
                "        document.getElementById('sqlCode').innerText = data.sql || '(No SQL executed)';\n" +
                "        const th = document.getElementById('tableHeaders');\n" +
                "        const tb = document.getElementById('tableBody');\n" +
                "        th.innerHTML = '';\n" +
                "        tb.innerHTML = '';\n" +
                "        (data.columns || []).forEach(c => {\n" +
                "          const colEl = document.createElement('th');\n" +
                "          colEl.innerText = c;\n" +
                "          th.appendChild(colEl);\n" +
                "        });\n" +
                "        (data.rows || []).forEach(row => {\n" +
                "          const tr = document.createElement('tr');\n" +
                "          row.forEach(val => {\n" +
                "            const td = document.createElement('td');\n" +
                "            td.innerText = (val !== null ? val : 'NULL');\n" +
                "            tr.appendChild(td);\n" +
                "          });\n" +
                "          tb.appendChild(tr);\n" +
                "        });\n" +
                "      } catch (e) { alert('Error: ' + e); }\n" +
                "    }\n" +
                "    window.onload = loadSchema;\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
