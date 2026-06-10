package onlexnet.sejmapi;

import java.util.UUID;

/**
 * Input payload for the demo durable workflow.
 *
 * @param correlationId correlation identifier for status tracking
 * @param sampleSize    number of demo rows requested by the caller
 */
public record DemoWorkflowRequest(String correlationId, int sampleSize) {

    /** Default row count for demo payload generation. */
    private static final int DEFAULT_SAMPLE_SIZE = 3;
    /** Upper bound for demo payload generation. */
    private static final int MAX_SAMPLE_SIZE = 20;

    /**
     * Creates a safe default request for local demo execution.
     *
     * @return default demo request
     */
    public static DemoWorkflowRequest defaultRequest() {
        return new DemoWorkflowRequest(generateCorrelationId(),
                DEFAULT_SAMPLE_SIZE);
    }

    /**
     * Normalizes null or out-of-range values for the HTTP starter path.
     *
     * The starter may generate a random correlation id because it is not
     * replayed by the Durable orchestrator runtime.
     *
     * @param request incoming request or null
     * @return normalized request
     */
    public static DemoWorkflowRequest normalize(
            final DemoWorkflowRequest request) {
        if (request == null) {
            return defaultRequest();
        }

        return new DemoWorkflowRequest(
                normalizeCorrelationId(request.correlationId()),
                normalizeSampleSize(request.sampleSize()));
    }

    /**
     * Maps OpenAPI-generated request model to the internal workflow request.
     *
     * @param request generated request model or null
     * @return internal workflow request
     */
    public static DemoWorkflowRequest fromOpenApi(DemoWorkflowRequest request) {
        if (request == null) {
            return null;
        }

        return new DemoWorkflowRequest(request.correlationId(), request.sampleSize());
    }

    /**
     * Normalizes request values for the orchestrator path without randomness.
     *
     * @param request               incoming request or null
     * @param fallbackCorrelationId deterministic correlation id to use when
     *                              the request does not provide one
     * @return normalized request
     */
    public static DemoWorkflowRequest normalizeForOrchestrator(
            final DemoWorkflowRequest request,
            final String fallbackCorrelationId) {
        if (request == null) {
            return new DemoWorkflowRequest(
                    normalizeFallbackCorrelationId(fallbackCorrelationId),
                    DEFAULT_SAMPLE_SIZE);
        }

        return new DemoWorkflowRequest(
                normalizeDeterministicCorrelationId(
                        request.correlationId(), fallbackCorrelationId),
                normalizeSampleSize(request.sampleSize()));
    }

    private static int normalizeSampleSize(final int sampleSize) {
        return sampleSize <= 0 ? DEFAULT_SAMPLE_SIZE
                : Math.min(sampleSize, MAX_SAMPLE_SIZE);
    }

    private static String normalizeCorrelationId(final String correlationId) {
        return correlationId == null || correlationId.isBlank()
                ? generateCorrelationId()
                : correlationId;
    }

    private static String normalizeDeterministicCorrelationId(
            final String correlationId,
            final String fallbackCorrelationId) {
        return correlationId == null || correlationId.isBlank()
                ? normalizeFallbackCorrelationId(fallbackCorrelationId)
                : correlationId;
    }

    private static String normalizeFallbackCorrelationId(
            final String fallbackCorrelationId) {
        return fallbackCorrelationId == null || fallbackCorrelationId.isBlank()
                ? "demo-unknown"
                : fallbackCorrelationId;
    }

    private static String generateCorrelationId() {
        return "demo-" + UUID.randomUUID();
    }
}
