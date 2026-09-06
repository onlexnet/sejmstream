package onlexnet.app.usecases;

import java.time.Duration;

/**
 * Ensures at most one collect orchestration runs at a time.
 * Additional requests received while a run is in progress are coalesced.
 */
public final class CollectCoordinatorDecider {

    public Decision decide(State state, Command command) {
        return switch (command) {
            case RequestCollect requestCollect -> onRequestCollect(state, requestCollect);
            case CollectCompleted _ -> onCollectFinished(state);
            case CollectFailed collectFailed -> onCollectFailed(state, collectFailed);
            case ForceStartNext forceStartNext -> onForceStartNext(state, forceStartNext);
        };
    }

    private Decision onRequestCollect(State state, RequestCollect requestCollect) {
        if (state.running()) {
            return new Decision(state, Effect.none());
        }

        return new Decision(new State(true), new Effect.StartCollectRun(requestCollect.source()));
    }

    private Decision onCollectFinished(State state) {
        return new Decision(State.idle(), Effect.none());
    }

    private Decision onCollectFailed(State state, CollectFailed collectFailed) {
        if (isTimeoutFailure(collectFailed.message())) {
            return new Decision(
                    State.idle(),
                    new Effect.StartCollectRunDelayed("timeout-retry", Duration.ofHours(1)));
        }

        return onCollectFinished(state);
    }

    private static boolean isTimeoutFailure(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.toLowerCase().contains("timeout");
    }

    private Decision onForceStartNext(State state, ForceStartNext forceStartNext) {
        return new Decision(new State(true), new Effect.StartCollectRun(forceStartNext.source()));
    }

    public record State(boolean running) {
        public static State idle() {
            return new State(false);
        }
    }

    public sealed interface Command permits RequestCollect, CollectCompleted, CollectFailed, ForceStartNext {
    }

    public record RequestCollect(String source) implements Command {
    }

    public record CollectCompleted(String orchestrationInstanceId) implements Command {
    }

    public record CollectFailed(String orchestrationInstanceId, String message) implements Command {
    }

    public record ForceStartNext(String source) implements Command {
    }

    public record Decision(State state, Effect effect) {
    }

    public sealed interface Effect permits Effect.None, Effect.StartCollectRun, Effect.StartCollectRunDelayed {
        static Effect none() {
            return None.INSTANCE;
        }

        enum None implements Effect {
            INSTANCE
        }

        record StartCollectRun(String source) implements Effect {
        }

        record StartCollectRunDelayed(String source, Duration delay) implements Effect {
        }
    }
}
