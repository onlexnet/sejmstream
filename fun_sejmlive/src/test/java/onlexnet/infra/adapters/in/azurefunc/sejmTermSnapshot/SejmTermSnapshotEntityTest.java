package onlexnet.infra.adapters.in.azurefunc.sejmTermSnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.TaskEntityContext;
import com.microsoft.durabletask.TaskEntityOperation;

class SejmTermSnapshotEntityTest {

    @Test
    void givenKnownOperationName_whenResolving_thenReturnsExpectedBinding() {
        assertThat(SejmTermSnapshotEntity.resolveContractOperation(
                SejmTermSnapshotContractOperations.TERM_SNAPSHOT_COLLECTED.methodName()))
                .isEqualTo(SejmTermSnapshotContractOperations.TERM_SNAPSHOT_COLLECTED);
    }

    @Test
    void givenUnknownOperationName_whenResolving_thenThrowsWithEntityNameAndOperation() {
        assertThatThrownBy(() -> SejmTermSnapshotEntity.resolveContractOperation("unknownMethod"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("SejmTermSnapshotEntity")
                .hasMessageContaining("unknownMethod");
    }

    @Test
    void givenIncomingTermSnapshotCollectedEvent_whenInvoked_thenCallsContractMethod() {
        var target = mock(SejmTermSnapshotContractV1.class);
        var operation = mock(TaskEntityOperation.class);
        var context = mock(TaskEntityContext.class);
        when(operation.getContext()).thenReturn(context);
        when(context.getId()).thenReturn(new EntityInstanceId("sejmTermSnapshot", "10"));
        var event = new TermSnapshotCollectedEvent(
                LocalDate.of(2026, 8, 27),
                "timer",
                "instance-1",
                Map.of("77", "hash-1"),
                List.of("301"),
                List.of("401"),
                List.of("501"));
        when(operation.getInput(TermSnapshotCollectedEvent.class)).thenReturn(event);

        SejmTermSnapshotContractOperations.TERM_SNAPSHOT_COLLECTED.invoke(target, operation);

        verify(target).termSnapshotCollected(event);
    }

    @Test
    void givenPreviousSnapshotForSameTerm_whenReconciling_thenDetectsNewAndUpdatedItems() {
        var state = new SejmTermSnapshotState();
        state.setLatestSnapshot(
                new TermSnapshotPayload(
                        10,
                        LocalDate.of(2026, 8, 27),
                        Map.of("77", "hash-old", "78", "hash-same"),
                        List.of("301"),
                        List.of("401"),
                        List.of("501")));

        var event = new TermSnapshotCollectedEvent(
                LocalDate.of(2026, 8, 27),
                "timer",
                "instance-1",
                Map.of("77", "hash-new", "78", "hash-same", "79", "hash-79"),
                List.of("301", "302"),
                List.of("401", "402"),
                List.of("501", "502"));

        var outcome = SejmTermSnapshotEntity.reconcile(state, 10, event);

        assertThat(outcome.diff().newInterpellations()).containsExactly("79");
        assertThat(outcome.diff().updatedInterpellations()).containsExactly("77");
        assertThat(outcome.diff().newWrittenQuestions()).containsExactly("302");
        assertThat(outcome.diff().newPrints()).containsExactly("402");
        assertThat(outcome.diff().newBills()).containsExactly("502");
    }

    @Test
    void givenDiffWithAllEvents_whenDispatching_thenInvokesSeparatedHandlers() {
        var probe = new DispatchProbeEntity();
        var diff = new SejmTermSnapshotEntity.TermSnapshotDiff(
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
    }

    private static final class DispatchProbeEntity extends SejmTermSnapshotEntity {
        private int newInterpellationsEvents;
        private int updatedInterpellationsEvents;
        private int newWrittenQuestionsEvents;
        private int newPrintsEvents;
        private int newBillsEvents;

        @Override
        protected void onNewInterpellationsDetected(NewInterpellationsDetectedEvent event) {
            this.newInterpellationsEvents++;
        }

        @Override
        protected void onInterpellationsUpdated(InterpellationsUpdatedEvent event) {
            this.updatedInterpellationsEvents++;
        }

        @Override
        protected void onNewWrittenQuestionsDetected(NewWrittenQuestionsDetectedEvent event) {
            this.newWrittenQuestionsEvents++;
        }

        @Override
        protected void onNewPrintsDetected(NewPrintsDetectedEvent event) {
            this.newPrintsEvents++;
        }

        @Override
        protected void onNewBillsDetected(NewBillsDetectedEvent event) {
            this.newBillsEvents++;
        }
    }
}