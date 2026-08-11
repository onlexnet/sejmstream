package onlexnet.infra.adapters.in;

import com.microsoft.azure.functions.ExecutionContext;

/**
 * Utility methods for logging through Azure Functions execution contexts.
 */
public final class Logger {

    private Logger() {
    }

    /**
     * Logs an informational message using the provided execution context.
     *
     * @param executionContext Azure Functions execution context used for logging
     * @param message message to log
     */
    public static void info(final ExecutionContext executionContext, final String message) {
        executionContext.getLogger().info(message);
    }
}
