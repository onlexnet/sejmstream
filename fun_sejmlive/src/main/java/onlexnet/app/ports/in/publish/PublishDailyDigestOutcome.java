package onlexnet.app.ports.in.publish;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Channel-agnostic outcome emitted by daily digest publishing.
 */
public sealed interface PublishDailyDigestOutcome permits PublishDailyDigestOutcome.Published,
        PublishDailyDigestOutcome.SkippedAlreadyPublished,
        PublishDailyDigestOutcome.SkippedNoDigest,
        PublishDailyDigestOutcome.Failed {

    LocalDate date();

    record Published(LocalDate date, String message) implements PublishDailyDigestOutcome {
        public Published {
            date = Objects.requireNonNull(date, "date cannot be null");
            message = Objects.requireNonNull(message, "message cannot be null");
        }
    }

    record SkippedAlreadyPublished(LocalDate date) implements PublishDailyDigestOutcome {
        public SkippedAlreadyPublished {
            date = Objects.requireNonNull(date, "date cannot be null");
        }
    }

    record SkippedNoDigest(LocalDate date) implements PublishDailyDigestOutcome {
        public SkippedNoDigest {
            date = Objects.requireNonNull(date, "date cannot be null");
        }
    }

    record Failed(LocalDate date, RuntimeException exception) implements PublishDailyDigestOutcome {
        public Failed {
            date = Objects.requireNonNull(date, "date cannot be null");
            exception = Objects.requireNonNull(exception, "exception cannot be null");
        }
    }
}