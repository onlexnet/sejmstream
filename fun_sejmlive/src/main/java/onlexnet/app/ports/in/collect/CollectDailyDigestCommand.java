package onlexnet.app.ports.in.collect;

import java.time.LocalDate;

/**
 * Input command for collecting daily Sejm activity.
 */
public record CollectDailyDigestCommand(LocalDate date) {
}
