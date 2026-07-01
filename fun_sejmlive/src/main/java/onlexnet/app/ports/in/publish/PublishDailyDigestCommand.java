package onlexnet.app.ports.in.publish;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Canonical request for publishing one day's Sejm digest.
 */
public record PublishDailyDigestCommand(LocalDate date) {

    public PublishDailyDigestCommand {
        date = Objects.requireNonNull(date, "date cannot be null");
    }
}