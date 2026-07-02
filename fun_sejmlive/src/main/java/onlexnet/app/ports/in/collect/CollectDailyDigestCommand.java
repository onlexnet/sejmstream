package onlexnet.app.ports.in.collect;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Input command for collecting daily Sejm activity.
 */
public record CollectDailyDigestCommand(LocalDate date) {

    public CollectDailyDigestCommand {
        date = Objects.requireNonNull(date, "date cannot be null");
    }
}
