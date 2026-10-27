package onlexnet.app.ports.in.ingest;

import java.time.LocalDate;

/**
 * Requests ingestion of Sejm activity for a specific date into the daily digest.
 */
public record IngestDailyDigestCommand(LocalDate date) {
}
