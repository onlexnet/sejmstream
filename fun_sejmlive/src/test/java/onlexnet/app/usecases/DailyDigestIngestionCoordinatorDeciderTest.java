package onlexnet.app.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class DailyDigestIngestionCoordinatorDeciderTest {

    private final DailyDigestIngestionCoordinatorDecider decider = new DailyDigestIngestionCoordinatorDecider();

    @Test
    void givenIdleState_whenRequestIngestion_thenStartsRunAndMarksRunning() {
        var decision = this.decider.decide(
                DailyDigestIngestionCoordinatorDecider.State.idle(),
                new DailyDigestIngestionCoordinatorDecider.RequestIngestion("timer"));

        assertThat(decision.state()).isEqualTo(new DailyDigestIngestionCoordinatorDecider.State(true));
        assertThat(decision.effect())
                .isEqualTo(new DailyDigestIngestionCoordinatorDecider.Effect.StartIngestionRun("timer"));
    }

    @Test
    void givenRunningState_whenRequestIngestion_thenKeepsRunningWithoutStartingAnotherRun() {
        var decision = this.decider.decide(
                new DailyDigestIngestionCoordinatorDecider.State(true),
                new DailyDigestIngestionCoordinatorDecider.RequestIngestion("http"));

        assertThat(decision.state()).isEqualTo(new DailyDigestIngestionCoordinatorDecider.State(true));
        assertThat(decision.effect()).isEqualTo(DailyDigestIngestionCoordinatorDecider.Effect.none());
    }

    @Test
    void givenRunningState_whenIngestionCompleted_thenGoesIdleWithoutStartingNewRun() {
        var decision = this.decider.decide(
                new DailyDigestIngestionCoordinatorDecider.State(true),
                new DailyDigestIngestionCoordinatorDecider.IngestionCompleted("instance-1"));

        assertThat(decision.state()).isEqualTo(DailyDigestIngestionCoordinatorDecider.State.idle());
        assertThat(decision.effect()).isEqualTo(DailyDigestIngestionCoordinatorDecider.Effect.none());
    }

        @Test
        void givenRunningState_whenIngestionFailedWithTimeout_thenSchedulesDelayedRunAndGoesIdle() {
                var decision = this.decider.decide(
                                new DailyDigestIngestionCoordinatorDecider.State(true),
                                new DailyDigestIngestionCoordinatorDecider.IngestionFailed("instance-1", "ReadTimeoutException"));

                assertThat(decision.state()).isEqualTo(DailyDigestIngestionCoordinatorDecider.State.idle());
                assertThat(decision.effect())
                                .isEqualTo(new DailyDigestIngestionCoordinatorDecider.Effect.StartIngestionRunDelayed(
                                                "timeout-retry",
                                                Duration.ofHours(1)));
        }

        @Test
        void givenRunningState_whenIngestionFailedWithoutTimeout_thenDoesNotScheduleDelayedRun() {
                var decision = this.decider.decide(
                                new DailyDigestIngestionCoordinatorDecider.State(true),
                                new DailyDigestIngestionCoordinatorDecider.IngestionFailed("instance-1", "validation error"));

                assertThat(decision.state()).isEqualTo(DailyDigestIngestionCoordinatorDecider.State.idle());
                assertThat(decision.effect()).isEqualTo(DailyDigestIngestionCoordinatorDecider.Effect.none());
        }

        @Test
        void givenRunningState_whenIngestionFailed_thenGoesIdle() {
        var decision = this.decider.decide(
                new DailyDigestIngestionCoordinatorDecider.State(true),
                new DailyDigestIngestionCoordinatorDecider.IngestionFailed("instance-1", "boom"));

        assertThat(decision.state()).isEqualTo(DailyDigestIngestionCoordinatorDecider.State.idle());
        assertThat(decision.effect()).isEqualTo(DailyDigestIngestionCoordinatorDecider.Effect.none());
    }

    @Test
    void givenStuckRunningState_whenForceStartNextIngestion_thenStartsRun() {
        var decision = this.decider.decide(
                new DailyDigestIngestionCoordinatorDecider.State(true),
                new DailyDigestIngestionCoordinatorDecider.ForceStartNextIngestion("manual-recovery"));

        assertThat(decision.state()).isEqualTo(new DailyDigestIngestionCoordinatorDecider.State(true));
        assertThat(decision.effect())
                .isEqualTo(new DailyDigestIngestionCoordinatorDecider.Effect.StartIngestionRun("manual-recovery"));
    }

    @Test
    void givenIdleState_whenForceStartNextIngestion_thenStartsRunAndMarksRunning() {
        var decision = this.decider.decide(
                DailyDigestIngestionCoordinatorDecider.State.idle(),
                new DailyDigestIngestionCoordinatorDecider.ForceStartNextIngestion("manual-recovery"));

        assertThat(decision.state()).isEqualTo(new DailyDigestIngestionCoordinatorDecider.State(true));
        assertThat(decision.effect())
                .isEqualTo(new DailyDigestIngestionCoordinatorDecider.Effect.StartIngestionRun("manual-recovery"));
    }
}
