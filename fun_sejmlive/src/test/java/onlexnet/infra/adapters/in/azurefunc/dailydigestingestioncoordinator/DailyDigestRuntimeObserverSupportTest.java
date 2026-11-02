package onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.microsoft.durabletask.DurableEntityClient;
import com.microsoft.durabletask.DurableTaskClient;
import com.microsoft.durabletask.EntityMetadata;
import com.microsoft.durabletask.OrchestrationMetadata;
import com.microsoft.durabletask.OrchestrationRuntimeStatus;

import onlexnet.app.usecases.DailyDigestIngestionState;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorDispatchCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorRuntimeObservedCommandDTO;

class DailyDigestRuntimeObserverSupportTest {

    private final DurableTaskClient client = mock(DurableTaskClient.class);
    private final DurableEntityClient entities = mock(DurableEntityClient.class);
    private final EntityMetadata entity = mock(EntityMetadata.class);
    private final OrchestrationMetadata runtime = mock(OrchestrationMetadata.class);
    private final DailyDigestRuntimeObserverSupport support = new DailyDigestRuntimeObserverSupport();
    private final ArrayList<CollectCoordinatorDispatchCommandDTO> observations = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(client.getEntities()).thenReturn(entities);
        when(entities.getEntityMetadata(CoordinatorTestSupport.ENTITY_ID, true)).thenReturn(entity);
        when(entity.readStateAs(DailyDigestIngestionCoordinatorState.class)).thenReturn(
                DailyDigestIngestionCoordinatorState.of(new DailyDigestIngestionState.Owned(
                        "run", 2, "timer", Instant.parse("2026-10-28T10:00:00Z"), false)));
        when(client.getInstanceMetadata("run", false)).thenReturn(runtime);
        when(runtime.isInstanceFound()).thenReturn(true);
        when(runtime.getInstanceId()).thenReturn("run");
        doAnswer(invocation -> {
            observations.add(invocation.getArgument(2));
            return null;
        }).when(entities).signalEntity(eq(CoordinatorTestSupport.ENTITY_ID), eq("dispatch"), any());
    }

    @ParameterizedTest
    @EnumSource(OrchestrationRuntimeStatus.class)
    void shouldSignalOnlyCorrelatedRuntimeStatusWithoutStartingOrchestration(OrchestrationRuntimeStatus status) {
        when(runtime.getRuntimeStatus()).thenReturn(status);

        support.observe(client);

        assertThat(observations).hasSize(1);
        var observed = (CollectCoordinatorRuntimeObservedCommandDTO) observations.getFirst();
        assertThat(observed.getOrchestrationInstanceId()).isEqualTo("run");
        assertThat(observed.getRuntimeStatus().getValue()).isEqualTo(status.name());
        verify(client, never()).scheduleNewOrchestrationInstance(anyString(), any(), any());
    }

    @Test
    void shouldNotInferFailureFromMissingRuntimeMetadata() {
        when(runtime.isInstanceFound()).thenReturn(false);
        support.observe(client);
        when(client.getInstanceMetadata("run", false)).thenReturn(null);
        support.observe(client);
        assertThat(observations).isEmpty();
    }

    @Test
    void shouldReportQueryFailureWithoutSignalingOrUnlocking() {
        when(client.getInstanceMetadata("run", false)).thenThrow(new IllegalStateException("sidecar unavailable"));
        assertThatThrownBy(() -> support.observe(client)).hasMessage("sidecar unavailable");
        assertThat(observations).isEmpty();
    }

    @Test
    void shouldRejectMissingStatusAndMismatchedMetadata() {
        assertThatThrownBy(() -> support.observe(client)).isInstanceOf(IllegalStateException.class);
        when(runtime.getRuntimeStatus()).thenReturn(OrchestrationRuntimeStatus.FAILED);
        when(runtime.getInstanceId()).thenReturn("different-run");
        assertThatThrownBy(() -> support.observe(client)).isInstanceOf(IllegalStateException.class);
        assertThat(observations).isEmpty();
    }

    @Test
    void shouldReportMissingOrInvalidCoordinatorStateWithoutRuntimeQueries() {
        when(entities.getEntityMetadata(CoordinatorTestSupport.ENTITY_ID, true)).thenReturn(null);
        support.observe(client);
        when(entities.getEntityMetadata(CoordinatorTestSupport.ENTITY_ID, true)).thenReturn(entity);
        when(entity.readStateAs(DailyDigestIngestionCoordinatorState.class)).thenReturn(null);
        assertThatThrownBy(() -> support.observe(client)).isInstanceOf(IllegalStateException.class);
        when(entity.readStateAs(DailyDigestIngestionCoordinatorState.class))
                .thenThrow(new IllegalArgumentException("Invalid coordinator state"));
        assertThatThrownBy(() -> support.observe(client)).isInstanceOf(IllegalArgumentException.class);
        verify(client, never()).getInstanceMetadata(anyString(), anyBoolean());
        assertThat(observations).isEmpty();
    }

    @Test
    void shouldNotQueryRuntimeForIdleExhaustedOrRecoveryRequiredState() {
        for (var lifecycle : java.util.List.of(new DailyDigestIngestionState.Idle(),
                new DailyDigestIngestionState.Exhausted("last"),
                new DailyDigestIngestionState.RecoveryRequired(DailyDigestIngestionState.Reason.LEGACY_OWNERSHIP, ""))) {
            when(entity.readStateAs(DailyDigestIngestionCoordinatorState.class))
                    .thenReturn(DailyDigestIngestionCoordinatorState.of(lifecycle));
            support.observe(client);
        }
        verify(client, never()).getInstanceMetadata(anyString(), anyBoolean());
        assertThat(observations).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = OrchestrationRuntimeStatus.class, names = {"COMPLETED", "FAILED"})
    void shouldRecoverLostNotificationsAndIgnoreDuplicateObservationsAfterRestart(
            OrchestrationRuntimeStatus status) throws Exception {
        when(runtime.getRuntimeStatus()).thenReturn(status);
        support.observe(client);
        support.observe(client);

        var initial = CoordinatorTestSupport.state(new DailyDigestIngestionState.Owned(
                "run", 2, "timer", Instant.parse("2026-10-28T10:00:00Z"), false));
        var result = CoordinatorTestSupport.run(initial, observations.toArray(CollectCoordinatorDispatchCommandDTO[]::new));
        if (status == OrchestrationRuntimeStatus.FAILED) {
            assertThat(result.getActionsCount()).isEqualTo(1);
            assertThat(((DailyDigestIngestionState.Owned) CoordinatorTestSupport.lifecycle(result)).attempt()).isEqualTo(3);
        } else {
            assertThat(result.getActionsList()).isEmpty();
            assertThat(CoordinatorTestSupport.lifecycle(result)).isEqualTo(new DailyDigestIngestionState.Idle());
        }
        var restarted = CoordinatorTestSupport.run(result.getEntityState().getValue(), observations.getFirst());
        assertThat(restarted.getActionsList()).isEmpty();
        assertThat(CoordinatorTestSupport.lifecycle(restarted)).isEqualTo(CoordinatorTestSupport.lifecycle(result));
    }

    @ParameterizedTest
    @EnumSource(value = OrchestrationRuntimeStatus.class,
            names = {"PENDING", "RUNNING", "SUSPENDED", "CONTINUED_AS_NEW", "TERMINATED", "CANCELED"})
    void shouldReconcileObservedStatusesWithoutStartingAnotherRun(OrchestrationRuntimeStatus status) throws Exception {
        when(runtime.getRuntimeStatus()).thenReturn(status);
        support.observe(client);
        var initialState = new DailyDigestIngestionState.Owned(
                "run", 2, "timer", Instant.parse("2026-10-28T10:00:00Z"), true);
        var result = CoordinatorTestSupport.run(CoordinatorTestSupport.state(initialState), observations.getFirst());
        assertThat(result.getActionsList()).isEmpty();
        if (status == OrchestrationRuntimeStatus.TERMINATED || status == OrchestrationRuntimeStatus.CANCELED) {
            assertThat(CoordinatorTestSupport.lifecycle(result)).isEqualTo(new DailyDigestIngestionState.RecoveryRequired(
                    DailyDigestIngestionState.Reason.ADMINISTRATIVE_STOP, "run"));
        } else {
            assertThat(CoordinatorTestSupport.lifecycle(result)).isEqualTo(initialState);
        }
    }
}
