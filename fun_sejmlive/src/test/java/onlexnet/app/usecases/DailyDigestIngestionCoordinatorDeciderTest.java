package onlexnet.app.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import onlexnet.app.usecases.DailyDigestIngestionCoordinatorDecider.Effect;
import onlexnet.app.usecases.DailyDigestIngestionCoordinatorDecider.ForceStartNextIngestion;
import onlexnet.app.usecases.DailyDigestIngestionCoordinatorDecider.IngestionCompleted;
import onlexnet.app.usecases.DailyDigestIngestionCoordinatorDecider.IngestionFailed;
import onlexnet.app.usecases.DailyDigestIngestionCoordinatorDecider.RequestIngestion;
import onlexnet.app.usecases.DailyDigestIngestionCoordinatorDecider.RuntimeObserved;
import onlexnet.app.usecases.DailyDigestIngestionCoordinatorDecider.RuntimeStatus;

class DailyDigestIngestionCoordinatorDeciderTest {

    private static final Instant START = Instant.parse("2026-10-28T10:00:00Z");
    private final DailyDigestIngestionCoordinatorDecider decider = new DailyDigestIngestionCoordinatorDecider();

    @Test
    void shouldRequestInitialStartOnlyWhenIdle() {
        var idle = new DailyDigestIngestionState.Idle();
        var decision = decider.decide(idle, new RequestIngestion("timer"));

        assertThat(decision.state()).isEqualTo(idle);
        assertThat(decision.effect()).isEqualTo(new Effect.StartIngestionRun("timer", 0, Duration.ZERO));
        assertUnchanged(owned(0), new RequestIngestion("http"));
        assertUnchanged(owned(3), new RequestIngestion("timer"));
    }

    @Test
    void shouldRetainOwnershipForMatchingNotificationHints() {
        var current = owned(2);
        for (var command : List.of(new IngestionCompleted("run-2"), new IngestionFailed("run-2", "timeout"))) {
            var decision = decider.decide(current, command);
            assertThat(decision.state()).isEqualTo(new DailyDigestIngestionState.Owned(
                    current.instanceId(), current.attempt(), current.source(), current.scheduledStart(), true));
            assertThat(decision.effect()).isEqualTo(Effect.None.INSTANCE);
        }
    }

    @Test
    void shouldIgnoreStaleSignalsAndTerminalObservations() {
        var current = owned(3);
        assertUnchanged(current, new IngestionCompleted("old"));
        assertUnchanged(current, new IngestionFailed("old", "boom"));
        assertUnchanged(current, new RuntimeObserved("old", RuntimeStatus.FAILED));
        assertUnchanged(current, new RuntimeObserved("old", RuntimeStatus.COMPLETED));
    }

    @ParameterizedTest
    @EnumSource(value = RuntimeStatus.class, names = {"PENDING", "RUNNING", "SUSPENDED", "CONTINUED_AS_NEW"})
    void shouldRetainOwnershipForNonterminalRuntimeStatus(RuntimeStatus status) {
        assertUnchanged(owned(4), new RuntimeObserved("run-4", status));
    }

    @Test
    void shouldBackOffFiveReplacementsThenExhaust() {
        var delays = List.of(1, 2, 4, 8, 15);
        for (var attempt = 0; attempt < delays.size(); attempt++) {
            var current = owned(attempt);
            var decision = decider.decide(current, new RuntimeObserved(current.instanceId(), RuntimeStatus.FAILED));
            assertThat(decision.state()).isEqualTo(current);
            assertThat(decision.effect()).isEqualTo(
                    new Effect.StartIngestionRun("timer", attempt + 1, Duration.ofMinutes(delays.get(attempt))));

            var committedReplacement = owned(attempt + 1);
            assertUnchanged(committedReplacement, new RuntimeObserved(current.instanceId(), RuntimeStatus.FAILED));
        }
        var exhausted = decider.decide(owned(5), new RuntimeObserved("run-5", RuntimeStatus.FAILED));
        assertThat(exhausted.state()).isEqualTo(new DailyDigestIngestionState.Exhausted("run-5"));
        assertThat(exhausted.effect()).isEqualTo(Effect.None.INSTANCE);
        assertUnchanged(exhausted.state(), new RequestIngestion("timer"));
        assertUnchanged(exhausted.state(), new RequestIngestion("http"));
        assertUnchanged(exhausted.state(), new RuntimeObserved("run-5", RuntimeStatus.FAILED));
    }

    @Test
    void shouldResetBudgetOnlyAfterConfirmedSuccess() {
        var completed = decider.decide(owned(5), new RuntimeObserved("run-5", RuntimeStatus.COMPLETED));
        assertThat(completed.state()).isEqualTo(new DailyDigestIngestionState.Idle());
        assertThat(completed.effect()).isEqualTo(Effect.None.INSTANCE);
        var next = decider.decide(completed.state(), new RequestIngestion("timer"));
        assertThat(next.effect()).isEqualTo(new Effect.StartIngestionRun("timer", 0, Duration.ZERO));
    }

    @Test
    void shouldRecoverExhaustionButNeverBypassOwnedOrLegacyState() {
        var command = new ForceStartNextIngestion("telegram-recovery");
        var decision = decider.decide(new DailyDigestIngestionState.Exhausted("run-5"), command);
        assertThat(decision.effect()).isEqualTo(new Effect.StartIngestionRun("telegram-recovery", 0, Duration.ZERO));
        var current = owned(3);
        var recovery = decider.decide(current, command);
        assertThat(recovery.effect()).isEqualTo(Effect.None.INSTANCE);
        assertThat(recovery.state()).isEqualTo(new DailyDigestIngestionState.Owned(
                current.instanceId(), current.attempt(), current.source(), current.scheduledStart(), true));
        var legacy = new DailyDigestIngestionState.RecoveryRequired(DailyDigestIngestionState.Reason.LEGACY_OWNERSHIP, "");
        assertUnchanged(legacy, command);
        assertUnchanged(legacy, new RequestIngestion("timer"));
    }

    @ParameterizedTest
    @EnumSource(value = RuntimeStatus.class, names = {"TERMINATED", "CANCELED"})
    void shouldRequireExplicitRecoveryForAdministrativeStop(RuntimeStatus status) {
        var stopped = decider.decide(owned(1), new RuntimeObserved("run-1", status));
        assertThat(stopped.state()).isEqualTo(new DailyDigestIngestionState.RecoveryRequired(
                DailyDigestIngestionState.Reason.ADMINISTRATIVE_STOP, "run-1"));
        assertThat(stopped.effect()).isEqualTo(Effect.None.INSTANCE);
        assertUnchanged(stopped.state(), new RequestIngestion("timer"));
        assertThat(decider.decide(stopped.state(), new ForceStartNextIngestion("admin")).effect())
                .isEqualTo(new Effect.StartIngestionRun("admin", 0, Duration.ZERO));
    }

    private void assertUnchanged(DailyDigestIngestionState state, DailyDigestIngestionCoordinatorDecider.Command command) {
        var decision = decider.decide(state, command);
        assertThat(decision.state()).isEqualTo(state);
        assertThat(decision.effect()).isEqualTo(Effect.None.INSTANCE);
    }

    private static DailyDigestIngestionState.Owned owned(int attempt) {
        return new DailyDigestIngestionState.Owned("run-" + attempt, attempt, "timer", START, false);
    }
}
