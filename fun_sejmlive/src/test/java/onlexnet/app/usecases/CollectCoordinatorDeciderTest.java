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

        assertThat(decision.state()).isEqualTo(new CollectCoordinatorDecider.State(true));
        assertThat(decision.effect())
                .isEqualTo(new CollectCoordinatorDecider.Effect.StartCollectRun("timer"));
    }

    @Test
    void givenRunningState_whenRequestCollect_thenKeepsRunningWithoutStartingAnotherRun() {
        var decision = this.decider.decide(
                new CollectCoordinatorDecider.State(true),
                new CollectCoordinatorDecider.RequestCollect("http"));

        assertThat(decision.state()).isEqualTo(new CollectCoordinatorDecider.State(true));
        assertThat(decision.effect()).isEqualTo(CollectCoordinatorDecider.Effect.none());
    }

    @Test
    void givenRunningState_whenCollectCompleted_thenGoesIdleWithoutStartingNewRun() {
        var decision = this.decider.decide(
                new CollectCoordinatorDecider.State(true),
                new CollectCoordinatorDecider.CollectCompleted("instance-1"));

        assertThat(decision.state()).isEqualTo(CollectCoordinatorDecider.State.idle());
        assertThat(decision.effect()).isEqualTo(CollectCoordinatorDecider.Effect.none());
    }

        @Test
        void givenRunningState_whenCollectFailed_thenGoesIdle() {
        var decision = this.decider.decide(
                new CollectCoordinatorDecider.State(true),
                new CollectCoordinatorDecider.CollectFailed("instance-1", "boom"));

        assertThat(decision.state()).isEqualTo(CollectCoordinatorDecider.State.idle());
        assertThat(decision.effect()).isEqualTo(CollectCoordinatorDecider.Effect.none());
    }

    @Test
    void givenStuckRunningState_whenForceStartNext_thenStartsRun() {
        var decision = this.decider.decide(
                new CollectCoordinatorDecider.State(true),
                new CollectCoordinatorDecider.ForceStartNext("manual-recovery"));

        assertThat(decision.state()).isEqualTo(new CollectCoordinatorDecider.State(true));
        assertThat(decision.effect())
                .isEqualTo(new CollectCoordinatorDecider.Effect.StartCollectRun("manual-recovery"));
    }

    @Test
    void givenIdleState_whenForceStartNext_thenStartsRunAndMarksRunning() {
        var decision = this.decider.decide(
                CollectCoordinatorDecider.State.idle(),
                new CollectCoordinatorDecider.ForceStartNext("manual-recovery"));

        assertThat(decision.state()).isEqualTo(new CollectCoordinatorDecider.State(true));
        assertThat(decision.effect())
                .isEqualTo(new CollectCoordinatorDecider.Effect.StartCollectRun("manual-recovery"));
    }
}
