package onlexnet.app.usecases;

import java.time.Duration;

/**
 * Ensures at most one daily-digest ingestion orchestration runs at a time.
 * Additional ingestion requests received while a run is in progress are coalesced.
 */
public final class DailyDigestIngestionCoordinatorDecider {

    public Decision decide(State state, Command command) {
        return switch (command) {
            case RequestIngestion requestIngestion -> onRequestIngestion(state, requestIngestion);
            case IngestionCompleted _ -> onIngestionFinished(state);
            case IngestionFailed ingestionFailed -> onIngestionFailed(state, ingestionFailed);
            case ForceStartNextIngestion forceStartNext -> onForceStartNext(state, forceStartNext);
        };
    }

    private Decision onRequestIngestion(State state, RequestIngestion requestIngestion) {
        if (state.running()) {
            return new Decision(state, Effect.none());
        }

        return new Decision(new State(true), new Effect.StartIngestionRun(requestIngestion.source()));
    }

    private Decision onIngestionFinished(State state) {
        return new Decision(State.idle(), Effect.none());
    }

    private Decision onIngestionFailed(State state, IngestionFailed ingestionFailed) {
        if (isTimeoutFailure(ingestionFailed.message())) {
            return new Decision(
                    State.idle(),
                    new Effect.StartIngestionRunDelayed("timeout-retry", Duration.ofHours(1)));
        }

        return onIngestionFinished(state);
    }

    private static boolean isTimeoutFailure(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.toLowerCase().contains("timeout");
    }

    private Decision onForceStartNext(State state, ForceStartNextIngestion forceStartNext) {
        return new Decision(new State(true), new Effect.StartIngestionRun(forceStartNext.source()));
    }

    public record State(boolean running) {
        public static State idle() {
            return new State(false);
        }
    }

    public sealed interface Command permits RequestIngestion, IngestionCompleted, IngestionFailed,
            ForceStartNextIngestion {
    }

    public record RequestIngestion(String source) implements Command {
    }

    public record IngestionCompleted(String orchestrationInstanceId) implements Command {
    }

    public record IngestionFailed(String orchestrationInstanceId, String message) implements Command {
    }

    public record ForceStartNextIngestion(String source) implements Command {
    }

    public record Decision(State state, Effect effect) {
    }

    public sealed interface Effect permits Effect.None, Effect.StartIngestionRun, Effect.StartIngestionRunDelayed {
        static Effect none() {
            return None.INSTANCE;
        }

        enum None implements Effect {
            INSTANCE
        }

        record StartIngestionRun(String source) implements Effect {
        }

        record StartIngestionRunDelayed(String source, Duration delay) implements Effect {
        }
    }
}
