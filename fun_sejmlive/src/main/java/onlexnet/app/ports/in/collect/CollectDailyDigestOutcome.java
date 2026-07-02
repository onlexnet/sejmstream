package onlexnet.app.ports.in.collect;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Channel-agnostic outcome emitted by daily collect execution.
 */
public sealed interface CollectDailyDigestOutcome permits CollectDailyDigestOutcome.TermMissing,
        CollectDailyDigestOutcome.Collected,
        CollectDailyDigestOutcome.Failed {

    String TYPE_VOTING = "VOTING";
    String TYPE_COMMITTEE_SITTING = "COMMITTEE_SITTING";
    String TYPE_PRINT = "PRINT";
    String TYPE_INTERPELLATION = "INTERPELLATION";
    String TYPE_WRITTEN_QUESTION = "WRITTEN_QUESTION";
    String TYPE_BILL = "BILL";

    LocalDate date();

    record TermMissing(LocalDate date) implements CollectDailyDigestOutcome {
        public TermMissing {
            date = Objects.requireNonNull(date, "date cannot be null");
        }
    }

    record Collected(LocalDate date, int termNum, Map<String, Integer> countsByType)
            implements CollectDailyDigestOutcome {
        public Collected {
            date = Objects.requireNonNull(date, "date cannot be null");
            countsByType = Map.copyOf(Objects.requireNonNull(countsByType, "countsByType cannot be null"));
        }
    }

    record Failed(LocalDate date, RuntimeException exception) implements CollectDailyDigestOutcome {
        public Failed {
            date = Objects.requireNonNull(date, "date cannot be null");
            exception = Objects.requireNonNull(exception, "exception cannot be null");
        }
    }
}
