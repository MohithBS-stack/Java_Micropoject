package com.nl2sql.routing;

/**
 * Interface for inspecting network and internet connectivity status.
 */
public interface ConnectivityChecker {

    /**
     * Checks whether internet connectivity is currently available.
     *
     * @return true if network connection is active, false otherwise
     */
    boolean isInternetAvailable();
}
