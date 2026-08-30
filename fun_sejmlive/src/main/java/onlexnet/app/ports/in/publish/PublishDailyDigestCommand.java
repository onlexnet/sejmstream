package onlexnet.app.ports.in.publish;

import java.time.LocalDate;

/**
 * Canonical request for publishing one day's Sejm digest.
 */
public record PublishDailyDigestCommand(LocalDate date) {
}