package onlexnet.app.ports.in.ingest;

import java.time.LocalDate;
import java.util.Map;

/**
 * Channel-agnostic outcome emitted by daily-digest ingestion.
 */
public sealed interface IngestDailyDigestOutcome permits IngestDailyDigestOutcome.TermMissing,
        IngestDailyDigestOutcome.Ingested,
        IngestDailyDigestOutcome.Failed {

    String TYPE_VOTING = "VOTING";
    String TYPE_COMMITTEE_SITTING = "COMMITTEE_SITTING";
    String TYPE_PRINT = "PRINT";
    String TYPE_INTERPELLATION = "INTERPELLATION";
    String TYPE_WRITTEN_QUESTION = "WRITTEN_QUESTION";
    String TYPE_BILL = "BILL";

    LocalDate date();

    record TermMissing(LocalDate date) implements IngestDailyDigestOutcome {
    }

    record Ingested(LocalDate date, int termNum, Map<String, Integer> countsByType)
            implements IngestDailyDigestOutcome {
        public Ingested {
            countsByType = Map.copyOf(countsByType);
        }
    }

    record Failed(LocalDate date, RuntimeException exception) implements IngestDailyDigestOutcome {
    }
}
