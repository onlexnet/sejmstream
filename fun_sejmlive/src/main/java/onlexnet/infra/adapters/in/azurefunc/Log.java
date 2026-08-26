package onlexnet.infra.adapters.in.azurefunc;

import com.microsoft.azure.functions.ExecutionContext;

/**
 * Utility methods for logging through Azure Functions execution contexts.
 */
public final class Log {

    private Log() {
    }

    /**
     * Logs an informational message using the provided execution context.
     *
     * @param execCtx Azure Functions execution context used for logging
     * @param message message to log
     */
    public static void info(ExecutionContext execCtx, String message) {
        execCtx.getLogger().info(message);
    }
}
