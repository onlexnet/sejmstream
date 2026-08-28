package onlexnet.app.usecases;

/**
 * Ensures at most one collect orchestration runs at a time and queues additional requests
 * so none are lost while a run is in progress.
 */
public final class CollectCoordinatorDecider {

    public Decision decide(State state, Command command) {
        return switch (command) {
            case RequestCollect requestCollect -> onRequestCollect(state, requestCollect);
            case CollectCompleted _ -> onCollectFinished(state);
            case CollectFailed _ -> onCollectFinished(state);
            case ForceStartNext forceStartNext -> onForceStartNext(state, forceStartNext);
        };
    }

    private Decision onRequestCollect(State state, RequestCollect requestCollect) {
        if (state.running()) {
            return new Decision(new State(true, state.pendingRequests() + 1), Effect.none());
        }

        return new Decision(new State(true, state.pendingRequests()), new Effect.StartCollectRun(requestCollect.source()));
    }

    private Decision onCollectFinished(State state) {
        if (state.pendingRequests() > 0) {
            return new Decision(
                    new State(true, state.pendingRequests() - 1),
                    new Effect.StartCollectRun("queued"));
        }

        return new Decision(new State(false, state.pendingRequests()), Effect.none());
    }

    private Decision onForceStartNext(State state, ForceStartNext forceStartNext) {
        if (state.pendingRequests() > 0) {
            return new Decision(
                    new State(true, state.pendingRequests() - 1),
                    new Effect.StartCollectRun(forceStartNext.source()));
        }

        return new Decision(new State(true, 0), new Effect.StartCollectRun(forceStartNext.source()));
    }

    public record State(boolean running, int pendingRequests) {
        public static State idle() {
            return new State(false, 0);
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

    public sealed interface Effect permits Effect.None, Effect.StartCollectRun {
        static Effect none() {
            return None.INSTANCE;
        }

        enum None implements Effect {
            INSTANCE
        }

        record StartCollectRun(String source) implements Effect {
        }
    }
}
