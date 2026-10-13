package com.nl2sql.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 24: Embedded Web Server & REST API Tests.
 */
public class WebServerTest {

    private static final int TEST_PORT = 18080;
    private static WebServer server;

    @BeforeAll
    public static void setUp() throws Exception {
        server = new WebServer(TEST_PORT, "data/company.db", false);
        server.start();
    }

    @AfterAll
    public static void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("1. GET / returns HTML web application")
    public void testGetRootHtml() throws Exception {
        URL url = URI.create("http://localhost:" + TEST_PORT + "/").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("text/html"));

        try (InputStream is = conn.getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains("Universal Text-to-SQL Pipeline"));
            assertTrue(content.contains("queryInput"));
        }
    }

    @Test
    @DisplayName("2. GET /api/schema returns table and column metadata")
    public void testGetApiSchema() throws Exception {
        URL url = URI.create("http://localhost:" + TEST_PORT + "/api/schema").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        try (InputStream is = conn.getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains("employees"));
            assertTrue(content.contains("columns"));
        }
    }

    @Test
    @DisplayName("3. POST /api/query executes query and returns structured response")
    public void testPostApiQuery() throws Exception {
        URL url = URI.create("http://localhost:" + TEST_PORT + "/api/query").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        String requestBody = "{\"question\": \"List all employees\", \"database\": \"data/company.db\", \"mode\": \"offline\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());
        try (InputStream is = conn.getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains("\"success\": true") || content.contains("\"success\":true"));
            assertTrue(content.contains("Alice Smith"));
            assertTrue(content.contains("SELECT"));
        }
    }
}
