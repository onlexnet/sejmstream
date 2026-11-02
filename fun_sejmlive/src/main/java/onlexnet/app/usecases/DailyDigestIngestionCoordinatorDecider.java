package onlexnet.app.usecases;

import java.time.Duration;
import java.util.List;

/**
 * Pure ownership decisions. A start effect becomes owned only when the adapter commits scheduling and its returned ID.
 */
public final class DailyDigestIngestionCoordinatorDecider {

    // Five replacements bound automatic recovery to six total runs per cycle (ADR-010).
    public static final int MAX_REPLACEMENTS = 5;
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(4),
            Duration.ofMinutes(8), Duration.ofMinutes(15));

    public Decision decide(DailyDigestIngestionState state, Command command) {
        return switch (command) {
            case RequestIngestion request -> state instanceof DailyDigestIngestionState.Idle
                    ? start(state, request.source(), 0, Duration.ZERO) : unchanged(state);
            case IngestionCompleted completed -> requestReconciliation(state, completed.orchestrationInstanceId());
            case IngestionFailed failed -> requestReconciliation(state, failed.orchestrationInstanceId());
            case ForceStartNextIngestion recovery -> recover(state, recovery.source());
            case RuntimeObserved observation -> observe(state, observation);
        };
    }

    private Decision recover(DailyDigestIngestionState state, String source) {
        return switch (state) {
            case DailyDigestIngestionState.Owned owned -> requestReconciliation(state, owned.instanceId());
            case DailyDigestIngestionState.RecoveryRequired recovery
                    when recovery.reason() == DailyDigestIngestionState.Reason.LEGACY_OWNERSHIP -> unchanged(state);
            default -> start(state, source, 0, Duration.ZERO);
        };
    }

    private Decision requestReconciliation(DailyDigestIngestionState state, String instanceId) {
        if (state instanceof DailyDigestIngestionState.Owned owned && owned.instanceId().equals(instanceId)) {
            return unchanged(new DailyDigestIngestionState.Owned(
                    owned.instanceId(), owned.attempt(), owned.source(), owned.scheduledStart(), true));
        }
        return unchanged(state);
    }

    private Decision observe(DailyDigestIngestionState state, RuntimeObserved observation) {
        if (!(state instanceof DailyDigestIngestionState.Owned owned)
                || !owned.instanceId().equals(observation.orchestrationInstanceId())) {
            return unchanged(state);
        }
        return switch (observation.status()) {
            case COMPLETED -> unchanged(new DailyDigestIngestionState.Idle());
            case FAILED -> owned.attempt() == MAX_REPLACEMENTS
                    ? unchanged(new DailyDigestIngestionState.Exhausted(owned.instanceId()))
                    : start(state, owned.source(), owned.attempt() + 1, RETRY_DELAYS.get(owned.attempt()));
            case TERMINATED, CANCELED -> unchanged(new DailyDigestIngestionState.RecoveryRequired(
                    DailyDigestIngestionState.Reason.ADMINISTRATIVE_STOP, owned.instanceId()));
            case PENDING, RUNNING, SUSPENDED, CONTINUED_AS_NEW -> unchanged(state);
        };
    }

    private static Decision start(DailyDigestIngestionState state, String source, int attempt, Duration delay) {
        return new Decision(state, new Effect.StartIngestionRun(source, attempt, delay));
    }

    private static Decision unchanged(DailyDigestIngestionState state) {
        return new Decision(state, Effect.None.INSTANCE);
    }

    public sealed interface Command {
    }

    public record RequestIngestion(String source) implements Command {
    }

    public record IngestionCompleted(String orchestrationInstanceId) implements Command {
    }

    public record IngestionFailed(String orchestrationInstanceId, String message) implements Command {
    }

    public record ForceStartNextIngestion(String source) implements Command {
    }

    public record RuntimeObserved(String orchestrationInstanceId, RuntimeStatus status) implements Command {
    }

    public enum RuntimeStatus {
        PENDING, RUNNING, SUSPENDED, CONTINUED_AS_NEW, COMPLETED, FAILED, TERMINATED, CANCELED
    }

    public record Decision(DailyDigestIngestionState state, Effect effect) {
    }

    public sealed interface Effect {
        enum None implements Effect { INSTANCE }

        record StartIngestionRun(String source, int attempt, Duration delay) implements Effect {
        }
    }
}
