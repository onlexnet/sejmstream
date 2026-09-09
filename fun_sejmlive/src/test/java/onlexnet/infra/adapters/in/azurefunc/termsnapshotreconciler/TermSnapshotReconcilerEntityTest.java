package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.TaskEntityContext;
import com.microsoft.durabletask.TaskEntityOperation;

import onlexnet.app.ports.out.ProjectOwnerNotifier;

class TermSnapshotReconcilerEntityTest {

    @Test
    void givenKnownOperationName_whenResolving_thenReturnsExpectedBinding() {
        assertThat(TermSnapshotReconcilerEntity.resolveContractOperation(
                TermSnapshotReconcilerContractOperations.TERM_SNAPSHOT_COLLECTED.methodName()))
                .isEqualTo(TermSnapshotReconcilerContractOperations.TERM_SNAPSHOT_COLLECTED);
    }

    @Test
    void givenUnknownOperationName_whenResolving_thenThrowsWithEntityNameAndOperation() {
        assertThatThrownBy(() -> TermSnapshotReconcilerEntity.resolveContractOperation("unknownMethod"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("TermSnapshotReconcilerEntity")
                .hasMessageContaining("unknownMethod");
    }

    @Test
    void givenIncomingTermSnapshotCollectedEvent_whenInvoked_thenCallsContractMethod() {
        var target = mock(TermSnapshotReconcilerContractV1.class);
        var operation = mock(TaskEntityOperation.class);
        var context = mock(TaskEntityContext.class);
        when(operation.getContext()).thenReturn(context);
        when(context.getId()).thenReturn(new EntityInstanceId("sejmTermSnapshot", "10"));
        var event = new TermSnapshotCollectedEvent(
            20260827,
                "timer",
                "instance-1",
                Map.of("77", "hash-1"),
                List.of("301"),
                List.of("401"),
                List.of("501"));
        when(operation.getInput(TermSnapshotCollectedEvent.class)).thenReturn(event);

        TermSnapshotReconcilerContractOperations.TERM_SNAPSHOT_COLLECTED.invoke(target, operation);

        verify(target).termSnapshotCollected(event);
    }

    @Test
    void givenPreviousSnapshotForSameTerm_whenReconciling_thenDetectsNewAndUpdatedItems() {
        var state = new TermSnapshotReconcilerState();
        state.setLatestSnapshot(
                new TermSnapshotPayload(
                        10,
                20260827,
                        Map.of("77", "hash-old", "78", "hash-same"),
                        List.of("301"),
                        List.of("401"),
                        List.of("501")));

        var event = new TermSnapshotCollectedEvent(
            20260827,
                "timer",
                "instance-1",
                Map.of("77", "hash-new", "78", "hash-same", "79", "hash-79"),
                List.of("301", "302"),
                List.of("401", "402"),
                List.of("501", "502"));

        var outcome = TermSnapshotReconcilerEntity.reconcile(state, 10, event);

        assertThat(outcome.diff().newInterpellations()).containsExactly("79");
        assertThat(outcome.diff().updatedInterpellations()).containsExactly("77");
        assertThat(outcome.diff().newWrittenQuestions()).containsExactly("302");
        assertThat(outcome.diff().newPrints()).containsExactly("402");
        assertThat(outcome.diff().newBills()).containsExactly("502");
    }

    @Test
    void givenDiffWithAllEvents_whenDispatching_thenInvokesSeparatedHandlers() {
        var ownerNotifier = mock(ProjectOwnerNotifier.class);
        var probe = new DispatchProbeEntity(ownerNotifier);
        var diff = new TermSnapshotReconcilerEntity.TermSnapshotDiff(
                10,
                List.of("79"),
                List.of("77"),
                List.of("302"),
                List.of("402"),
                List.of("502"));

        probe.dispatchRecognizedEvents(diff);

        assertThat(probe.newInterpellationsEvents).isEqualTo(1);
        assertThat(probe.updatedInterpellationsEvents).isEqualTo(1);
        assertThat(probe.newWrittenQuestionsEvents).isEqualTo(1);
        assertThat(probe.newPrintsEvents).isEqualTo(1);
        assertThat(probe.newBillsEvents).isEqualTo(1);
        verify(ownerNotifier).notifyOwner(contains("Term: 10"));
        verify(ownerNotifier).notifyOwner(contains("New interpellations: 1"));
        verify(ownerNotifier).notifyOwner(contains("Updated interpellations: 1"));
        verify(ownerNotifier).notifyOwner(contains("New written questions: 1"));
        verify(ownerNotifier).notifyOwner(contains("New prints: 1"));
        verify(ownerNotifier).notifyOwner(contains("New bills: 1"));
    }

    @Test
    void givenDiffWithoutChanges_whenDispatching_thenDoesNotNotifyOwner() {
        var ownerNotifier = mock(ProjectOwnerNotifier.class);
        var entity = new TermSnapshotReconcilerEntity(ownerNotifier);
        var diff = new TermSnapshotReconcilerEntity.TermSnapshotDiff(
                10,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        entity.dispatchRecognizedEvents(diff);

        verify(ownerNotifier, never()).notifyOwner(anyString());
    }

    private static final class DispatchProbeEntity extends TermSnapshotReconcilerEntity {
        private int newInterpellationsEvents;
        private int updatedInterpellationsEvents;
        private int newWrittenQuestionsEvents;
        private int newPrintsEvents;
        private int newBillsEvents;

        private DispatchProbeEntity(ProjectOwnerNotifier ownerNotifier) {
            super(ownerNotifier);
        }

        @Override
        protected void onNewInterpellationsDetected(NewInterpellationsDetectedEvent event) {
            newInterpellationsEvents++;
        }

        @Override
        protected void onInterpellationsUpdated(InterpellationsUpdatedEvent event) {
            updatedInterpellationsEvents++;
        }

        @Override
        protected void onNewWrittenQuestionsDetected(NewWrittenQuestionsDetectedEvent event) {
            newWrittenQuestionsEvents++;
        }

        @Override
        protected void onNewPrintsDetected(NewPrintsDetectedEvent event) {
            newPrintsEvents++;
        }

        @Override
        protected void onNewBillsDetected(NewBillsDetectedEvent event) {
            newBillsEvents++;
        }
    }
}