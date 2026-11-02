package onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import onlexnet.app.usecases.DailyDigestIngestionState;

/**
 * Versioned durable state envelope; legacy boolean-only payloads are unsupported.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DailyDigestIngestionCoordinatorState(int version, DailyDigestIngestionState lifecycle) {

    // Version one introduces correlated ownership; unknown versions must never initialize idle.
    private static final int CURRENT_VERSION = 1;

    public static DailyDigestIngestionCoordinatorState of(DailyDigestIngestionState lifecycle) {
        return new DailyDigestIngestionCoordinatorState(CURRENT_VERSION, lifecycle);
    }

    @JsonCreator
    static DailyDigestIngestionCoordinatorState decode(
            @JsonProperty("version") @Nullable Integer version,
            @JsonProperty("lifecycle") @Nullable DailyDigestIngestionState lifecycle) {
        if (version == null || version != CURRENT_VERSION || lifecycle == null) {
            throw new IllegalArgumentException("Invalid coordinator state version or lifecycle; legacy state is unsupported");
        }
        validate(lifecycle);
        return of(lifecycle);
    }

    private static void validate(DailyDigestIngestionState lifecycle) {
        switch (lifecycle) {
            case DailyDigestIngestionState.Owned owned -> {
                requireIdentity(owned.instanceId());
                if (owned.attempt() < 0 || owned.attempt() > 5 || owned.scheduledStart() == null
                        || owned.source() == null || owned.source().isBlank()) {
                    throw new IllegalArgumentException("Invalid owned coordinator state");
                }
            }
            case DailyDigestIngestionState.Exhausted exhausted -> requireIdentity(exhausted.instanceId());
            case DailyDigestIngestionState.RecoveryRequired recovery -> {
                if (recovery.reason() == null || recovery.instanceId() == null) {
                    throw new IllegalArgumentException("Invalid recovery-required coordinator state");
                }
                if (recovery.reason() == DailyDigestIngestionState.Reason.ADMINISTRATIVE_STOP) {
                    requireIdentity(recovery.instanceId());
                }
            }
            case DailyDigestIngestionState.Idle _ -> {
            }
        }
    }

    private static void requireIdentity(@Nullable String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("Owned orchestration instance ID is required");
        }
    }
}
