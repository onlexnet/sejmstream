package onlexnet.app.usecases;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Persisted collection ownership. Attempt zero is the initial run; attempts one through five are replacements.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DailyDigestIngestionState.Idle.class, name = "idle"),
        @JsonSubTypes.Type(value = DailyDigestIngestionState.Owned.class, name = "owned"),
        @JsonSubTypes.Type(value = DailyDigestIngestionState.Exhausted.class, name = "exhausted"),
        @JsonSubTypes.Type(value = DailyDigestIngestionState.RecoveryRequired.class, name = "recoveryRequired")
})
public sealed interface DailyDigestIngestionState {

    record Idle() implements DailyDigestIngestionState {
    }

    record Owned(
            String instanceId,
            int attempt,
            String source,
            Instant scheduledStart,
            boolean reconciliationRequested) implements DailyDigestIngestionState {
    }

    record Exhausted(String instanceId) implements DailyDigestIngestionState {
    }

    record RecoveryRequired(Reason reason, String instanceId) implements DailyDigestIngestionState {
    }

    enum Reason {
        LEGACY_OWNERSHIP,
        ADMINISTRATIVE_STOP
    }
}
