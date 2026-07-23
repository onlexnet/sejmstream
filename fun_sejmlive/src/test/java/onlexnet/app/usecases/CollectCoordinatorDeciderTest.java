package onlexnet.app.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CollectCoordinatorDeciderTest {

    private final CollectCoordinatorDecider decider = new CollectCoordinatorDecider();

    @Test
    void givenIdleState_whenRequestCollect_thenStartsRunAndMarksRunning() {
        var decision = this.decider.decide(
                CollectCoordinatorDecider.State.idle(),
                new CollectCoordinatorDecider.RequestCollect("timer"));

        assertThat(decision.state()).isEqualTo(new CollectCoordinatorDecider.State(true, 0));
        assertThat(decision.effect())
                .isEqualTo(new CollectCoordinatorDecider.Effect.StartCollectRun("timer"));
    }

    @Test
    void givenRunningState_whenRequestCollect_thenIncrementsPendingWithoutStartingRun() {
        var decision = this.decider.decide(
                new CollectCoordinatorDecider.State(true, 1),
                new CollectCoordinatorDecider.RequestCollect("http"));

        assertThat(decision.state()).isEqualTo(new CollectCoordinatorDecider.State(true, 2));
        assertThat(decision.effect()).isEqualTo(CollectCoordinatorDecider.Effect.none());
    }

    @Test
    void givenRunningStateWithPending_whenCollectCompleted_thenStartsQueuedRun() {
        var decision = this.decider.decide(
                new CollectCoordinatorDecider.State(true, 2),
                new CollectCoordinatorDecider.CollectCompleted("instance-1"));

        assertThat(decision.state()).isEqualTo(new CollectCoordinatorDecider.State(true, 1));
        assertThat(decision.effect())
                .isEqualTo(new CollectCoordinatorDecider.Effect.StartCollectRun("queued"));
    }

    @Test
    void givenRunningStateWithoutPending_whenCollectFailed_thenGoesIdle() {
        var decision = this.decider.decide(
                new CollectCoordinatorDecider.State(true, 0),
                new CollectCoordinatorDecider.CollectFailed("instance-1", "boom"));

        assertThat(decision.state()).isEqualTo(CollectCoordinatorDecider.State.idle());
        assertThat(decision.effect()).isEqualTo(CollectCoordinatorDecider.Effect.none());
    }
}
