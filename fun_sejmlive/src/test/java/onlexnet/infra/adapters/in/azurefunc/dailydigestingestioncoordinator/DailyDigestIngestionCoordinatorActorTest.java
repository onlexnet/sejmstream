package onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.microsoft.durabletask.TaskEntity;

import onlexnet.app.usecases.DailyDigestIngestionState;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletionDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectCompletedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorForceStartNextCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectRuntimeStatusDTO;

class DailyDigestIngestionCoordinatorActorTest {

    @Test
    void shouldResolveDispatchAndRejectUnknownOperation() {
        assertThat(DailyDigestIngestionCoordinatorActor.resolveContractOperation("DiSpAtCh"))
                .isEqualTo(DailyDigestIngestionCoordinatorContractOperations.DISPATCH);
        assertThatThrownBy(() -> DailyDigestIngestionCoordinatorActor.resolveContractOperation("unknown"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new DailyDigestIngestionCoordinatorActor(CoordinatorTestSupport.validator())
                .dispatch(CoordinatorTestSupport.request())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldCommitOneOwnedStartForRepeatedRequestsInOneBatch() throws Exception {
        var result = CoordinatorTestSupport.run(CoordinatorTestSupport.state(new DailyDigestIngestionState.Idle()),
                CoordinatorTestSupport.request(), CoordinatorTestSupport.request());
        assertThat(result.getResultsList()).allMatch(operation -> operation.hasSuccess());
        assertThat(result.getActionsCount()).isEqualTo(1);
        var action = result.getActions(0).getStartNewOrchestration();
        var owned = (DailyDigestIngestionState.Owned) CoordinatorTestSupport.lifecycle(result);
        assertThat(action.getName()).isEqualTo(SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME);
        assertThat(owned.instanceId()).isEqualTo(action.getInstanceId()).isNotBlank();
        assertThat(owned.attempt()).isZero();
        assertThat(owned.scheduledStart().getEpochSecond()).isEqualTo(action.getScheduledTime().getSeconds());
    }

    @Test
    void shouldOwnDelayedReplacementAndIgnoreRepeatedFailureAndRequestsAfterRestart() throws Exception {
        var original = owned("original", 0);
        var failure = CoordinatorTestSupport.observation("original", CollectRuntimeStatusDTO.FAILED);
        var before = Instant.now();
        var result = CoordinatorTestSupport.run(CoordinatorTestSupport.state(original), failure, failure,
                CoordinatorTestSupport.request());
        assertThat(result.getActionsCount()).isEqualTo(1);
        var replacement = (DailyDigestIngestionState.Owned) CoordinatorTestSupport.lifecycle(result);
        assertThat(replacement.instanceId()).isNotEqualTo("original");
        assertThat(replacement.attempt()).isEqualTo(1);
        assertThat(replacement.scheduledStart()).isAfterOrEqualTo(before.plusSeconds(60));

        var restarted = CoordinatorTestSupport.run(result.getEntityState().getValue(), CoordinatorTestSupport.request(), failure);
        assertThat(restarted.getActionsList()).isEmpty();
        assertThat(CoordinatorTestSupport.lifecycle(restarted)).isEqualTo(replacement);
    }

    @Test
    void shouldTreatCompletionAsHintUntilRuntimeConfirmsCompletion() throws Exception {
        var completion = new CollectCompletionDTO();
        completion.setOrchestrationInstanceId("run");
        var command = new CollectCoordinatorCollectCompletedCommandDTO();
        command.setCompletion(completion);
        var result = CoordinatorTestSupport.run(CoordinatorTestSupport.state(owned("run", 2)), command,
                CoordinatorTestSupport.request());
        var hinted = (DailyDigestIngestionState.Owned) CoordinatorTestSupport.lifecycle(result);
        assertThat(hinted.reconciliationRequested()).isTrue();
        assertThat(result.getActionsList()).isEmpty();

        var completed = CoordinatorTestSupport.run(result.getEntityState().getValue(),
                CoordinatorTestSupport.observation("run", CollectRuntimeStatusDTO.COMPLETED));
        assertThat(CoordinatorTestSupport.lifecycle(completed)).isEqualTo(new DailyDigestIngestionState.Idle());
    }

    @Test
    void shouldRollbackScheduledActionAndStateWhenOperationFails() throws Exception {
        var gateway = CoordinatorTestSupport.gateway();
        TaskEntity failAfterScheduling = operation -> {
            gateway.run(operation);
            throw new IllegalStateException("Injected failure after scheduling and state write");
        };
        var initial = CoordinatorTestSupport.state(owned("failed", 2));
        var result = CoordinatorTestSupport.run(failAfterScheduling, initial,
                CoordinatorTestSupport.observation("failed", CollectRuntimeStatusDTO.FAILED));
        assertThat(result.getResults(0).hasFailure()).isTrue();
        assertThat(result.getActionsList()).isEmpty();
        assertThat(result.getEntityState().getValue()).isEqualTo(initial);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectBothLegacyStatesEvenForManualRecovery(boolean running) throws Exception {
        var recovery = new CollectCoordinatorForceStartNextCommandDTO();
        recovery.setSource("admin");
        var legacyState = "{\"running\":" + running + "}";
        var result = CoordinatorTestSupport.run(legacyState, CoordinatorTestSupport.request(), recovery);
        assertThat(result.getResultsList()).allMatch(operation -> operation.hasFailure());
        assertThat(result.getActionsList()).isEmpty();
        assertThat(result.getEntityState().getValue()).isEqualTo(legacyState);
    }

    @Test
    void shouldRejectMalformedObservationWithoutChangingOwnership() throws Exception {
        var initial = CoordinatorTestSupport.state(owned("run", 1));
        var invalid = CoordinatorTestSupport.observation(" ", CollectRuntimeStatusDTO.FAILED);
        var result = CoordinatorTestSupport.run(initial, invalid);
        assertThat(result.getResults(0).hasFailure()).isTrue();
        assertThat(result.getActionsList()).isEmpty();
        assertThat(result.getEntityState().getValue()).isEqualTo(initial);
    }

    @Test
    void shouldExhaustAndRequireExplicitRecoveryWithoutBypassingScheduledOwner() throws Exception {
        var result = CoordinatorTestSupport.run(CoordinatorTestSupport.state(owned("last", 5)),
                CoordinatorTestSupport.observation("last", CollectRuntimeStatusDTO.FAILED), CoordinatorTestSupport.request());
        assertThat(CoordinatorTestSupport.lifecycle(result)).isEqualTo(new DailyDigestIngestionState.Exhausted("last"));
        assertThat(result.getActionsList()).isEmpty();

        var recovery = new CollectCoordinatorForceStartNextCommandDTO();
        recovery.setSource("telegram-recovery");
        var recovered = CoordinatorTestSupport.run(result.getEntityState().getValue(), recovery, recovery);
        assertThat(recovered.getActionsCount()).isEqualTo(1);
        assertThat(((DailyDigestIngestionState.Owned) CoordinatorTestSupport.lifecycle(recovered)).attempt()).isZero();
    }

    private static DailyDigestIngestionState.Owned owned(String instanceId, int attempt) {
        return new DailyDigestIngestionState.Owned(instanceId, attempt, "timer",
                Instant.parse("2026-10-28T10:00:00Z"), false);
    }
}
