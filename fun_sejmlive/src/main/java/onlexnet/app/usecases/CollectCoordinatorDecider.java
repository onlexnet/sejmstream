package onlexnet.app.usecases;

/**
 * Pure state machine for serialized collect orchestration scheduling.
 */
public final class CollectCoordinatorDecider {

    public Decision decide(final State state, final Command command) {
        return switch (command) {
            case RequestCollect requestCollect -> onRequestCollect(state, requestCollect);
            case CollectCompleted _ -> onCollectFinished(state);
            case CollectFailed _ -> onCollectFinished(state);
        };
    }

    private Decision onRequestCollect(final State state, final RequestCollect requestCollect) {
        if (state.running()) {
            return new Decision(new State(true, state.pendingRequests() + 1), Effect.none());
        }

        return new Decision(new State(true, state.pendingRequests()), new Effect.StartCollectRun(requestCollect.source()));
    }

    private Decision onCollectFinished(final State state) {
        if (state.pendingRequests() > 0) {
            return new Decision(
                    new State(true, state.pendingRequests() - 1),
                    new Effect.StartCollectRun("queued"));
        }

        return new Decision(new State(false, state.pendingRequests()), Effect.none());
    }

    public record State(boolean running, int pendingRequests) {
        public static State idle() {
            return new State(false, 0);
        }
    }

    public sealed interface Command permits RequestCollect, CollectCompleted, CollectFailed {
    }

    public record RequestCollect(String source) implements Command {
    }

    public record CollectCompleted(String orchestrationInstanceId) implements Command {
    }

    public record CollectFailed(String orchestrationInstanceId, String message) implements Command {
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
