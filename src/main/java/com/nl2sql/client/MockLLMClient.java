package com.nl2sql.client;

/**
 * Mock implementation of LLMClient for non-network unit testing.
 */
public class MockLLMClient implements LLMClient {

    private final boolean shouldThrow;
    private final String mockResponse;
    private boolean called = false;

    public MockLLMClient(boolean shouldThrow, String mockResponse) {
        this.shouldThrow = shouldThrow;
        this.mockResponse = mockResponse;
    }

    @Override
    public String generateContent(String prompt) throws Exception {
        this.called = true;
        if (shouldThrow) {
            throw new RuntimeException("Simulated LLM Client Network Error / Timeout");
        }
        return mockResponse;
    }

    public boolean wasCalled() {
        return called;
    }
}
