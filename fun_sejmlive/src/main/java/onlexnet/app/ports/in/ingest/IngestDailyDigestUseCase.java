package onlexnet.app.ports.in.ingest;

/**
 * Application use case for ingesting Sejm activity into the daily digest.
 */
public interface IngestDailyDigestUseCase {

    /**
     * Ingests Sejm activity for the specified date.
     */
    IngestDailyDigestOutcome ingest(IngestDailyDigestCommand command);
}
