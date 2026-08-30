package onlexnet.app.ports.in.publish;

import java.time.LocalDate;

/**
 * Channel-agnostic outcome emitted by daily digest publishing.
 */
public sealed interface PublishDailyDigestOutcome permits PublishDailyDigestOutcome.Published,
        PublishDailyDigestOutcome.SkippedAlreadyPublished,
        PublishDailyDigestOutcome.SkippedNoDigest,
        PublishDailyDigestOutcome.Failed {

    LocalDate date();

    record Published(LocalDate date, String message) implements PublishDailyDigestOutcome {
    }

    record SkippedAlreadyPublished(LocalDate date) implements PublishDailyDigestOutcome {
    }

    record SkippedNoDigest(LocalDate date) implements PublishDailyDigestOutcome {
    }

    record Failed(LocalDate date, RuntimeException exception) implements PublishDailyDigestOutcome {
    }
}