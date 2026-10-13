package com.nl2sql.routing;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Deterministic implementation of ConnectivityChecker.
 * Performs a fast socket connection test with a short timeout to detect network availability safely.
 */
public class DefaultConnectivityChecker implements ConnectivityChecker {

    private static final String DEFAULT_HOST = "generativelanguage.googleapis.com";
    private static final int DEFAULT_PORT = 443;
    private static final int TIMEOUT_MS = 1500;

    private final String host;
    private final int port;
    private final int timeoutMs;

    public DefaultConnectivityChecker() {
        this(DEFAULT_HOST, DEFAULT_PORT, TIMEOUT_MS);
    }

    public DefaultConnectivityChecker(String host, int port, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public boolean isInternetAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            // Secondary fallback to google.com port 443
            try (Socket fallbackSocket = new Socket()) {
                fallbackSocket.connect(new InetSocketAddress("google.com", 443), timeoutMs);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}
