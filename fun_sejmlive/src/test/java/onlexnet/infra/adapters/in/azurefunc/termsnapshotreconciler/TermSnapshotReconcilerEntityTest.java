package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
                Map.of("77", new TermSnapshotCollectedEvent.InterpellationPresentation("Interpelacja 77", "https://sejm.example/77")),
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
                Map.of(
                        "77", new TermSnapshotCollectedEvent.InterpellationPresentation("Tytul 77", "https://sejm.example/77"),
                        "78", new TermSnapshotCollectedEvent.InterpellationPresentation("Tytul 78", "https://sejm.example/78"),
                        "79", new TermSnapshotCollectedEvent.InterpellationPresentation("Tytul 79", "https://sejm.example/79")),
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
    void givenItemUntouchedForOneDay_whenReconcilingThirdDay_thenReportsUpdateNotNew() {
        var state = new TermSnapshotReconcilerState();

        // Day 1: interpellation "79" (and written question/print/bill "301"/"401"/"501") collected as new.
        var day1Event = new TermSnapshotCollectedEvent(
                20260101,
                "timer",
                "instance-1",
                Map.of("79", "hash-79-v1"),
                Map.of("79", new TermSnapshotCollectedEvent.InterpellationPresentation("Tytul 79", "https://sejm.example/79")),
                List.of("301"),
                List.of("401"),
                List.of("501"));
        var day1Outcome = TermSnapshotReconcilerEntity.reconcile(state, 10, day1Event);
        assertThat(day1Outcome.diff().newInterpellations()).containsExactly("79");
        assertThat(day1Outcome.diff().newWrittenQuestions()).containsExactly("301");
        assertThat(day1Outcome.diff().newPrints()).containsExactly("401");
        assertThat(day1Outcome.diff().newBills()).containsExactly("501");

        // Day 2: nothing related to "79"/"301"/"401"/"501" was touched.
        var day2Event = new TermSnapshotCollectedEvent(
                20260102,
                "timer",
                "instance-2",
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of());
        var day2Outcome = TermSnapshotReconcilerEntity.reconcile(state, 10, day2Event);
        assertThat(day2Outcome.diff().newInterpellations()).isEmpty();
        assertThat(day2Outcome.diff().updatedInterpellations()).isEmpty();

        // Day 3: "79" reappears with a changed fingerprint; "301"/"401"/"501" reappear unchanged.
        var day3Event = new TermSnapshotCollectedEvent(
                20260103,
                "timer",
                "instance-3",
                Map.of("79", "hash-79-v2"),
                Map.of("79", new TermSnapshotCollectedEvent.InterpellationPresentation("Tytul 79", "https://sejm.example/79")),
                List.of("301"),
                List.of("401"),
                List.of("501"));
        var day3Outcome = TermSnapshotReconcilerEntity.reconcile(state, 10, day3Event);

        assertThat(day3Outcome.diff().newInterpellations()).isEmpty();
        assertThat(day3Outcome.diff().updatedInterpellations()).containsExactly("79");
        assertThat(day3Outcome.diff().newWrittenQuestions()).isEmpty();
        assertThat(day3Outcome.diff().newPrints()).isEmpty();
        assertThat(day3Outcome.diff().newBills()).isEmpty();
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

        probe.dispatchRecognizedEvents(diff, new TermSnapshotCollectedEvent(
            20260907,
            "timer",
            "instance-10",
            Map.of("79", "hash-79", "77", "hash-77"),
            Map.of(
                "79", new TermSnapshotCollectedEvent.InterpellationPresentation("Nowa interpelacja", "https://sejm.example/79"),
                "77", new TermSnapshotCollectedEvent.InterpellationPresentation("Zmieniona interpelacja", "https://sejm.example/77")),
            List.of("302"),
            List.of("402"),
            List.of("502")));

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

        entity.dispatchRecognizedEvents(diff, new TermSnapshotCollectedEvent(
            20260907,
            "timer",
            "instance-11",
            Map.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of()));

        verify(ownerNotifier, never()).notifyOwner(anyString());
    }

    @Test
    void givenNewInterpellationsWithPresentation_whenHandling_thenSendsDetailedOwnerMessageWithLinks() {
        var ownerNotifier = mock(ProjectOwnerNotifier.class);
        var entity = new TermSnapshotReconcilerEntity(ownerNotifier);

        entity.onNewInterpellationsDetected(new TermSnapshotReconcilerEntity.NewInterpellationsDetectedEvent(
                10,
                List.of("79", "80"),
                Map.of(
                        "79", new TermSnapshotCollectedEvent.InterpellationPresentation("Tytul 79", "https://sejm.example/79"),
                        "80", new TermSnapshotCollectedEvent.InterpellationPresentation("Tytul 80", "https://sejm.example/80"))));

        verify(ownerNotifier).notifyOwner(contains("Nowe interpelacje wykryte"));
        verify(ownerNotifier).notifyOwner(contains("Kadencja: 10"));
        verify(ownerNotifier).notifyOwner(contains("- 79: Tytul 79"));
        verify(ownerNotifier).notifyOwner(contains("https://sejm.example/79"));
        verify(ownerNotifier).notifyOwner(contains("- 80: Tytul 80"));
    }

    @Test
    void givenMissingPresentationData_whenHandlingNewInterpellations_thenUsesFallbackValues() {
        var ownerNotifier = mock(ProjectOwnerNotifier.class);
        var entity = new TermSnapshotReconcilerEntity(ownerNotifier);

        entity.onNewInterpellationsDetected(new TermSnapshotReconcilerEntity.NewInterpellationsDetectedEvent(
                10,
                List.of("79"),
                Map.of("79", new TermSnapshotCollectedEvent.InterpellationPresentation("", null))));

        verify(ownerNotifier).notifyOwner(contains("brak tytułu"));
        verify(ownerNotifier).notifyOwner(contains("(brak linku)"));
    }

    @Test
    void givenLargeNewInterpellationList_whenHandling_thenSplitsDetailedNotificationIntoChunks() {
        var ownerNotifier = mock(ProjectOwnerNotifier.class);
        var entity = new TermSnapshotReconcilerEntity(ownerNotifier);

        var numbers = new java.util.ArrayList<String>();
        var presentation = new java.util.HashMap<String, TermSnapshotCollectedEvent.InterpellationPresentation>();
        for (int i = 1; i <= 250; i++) {
            var num = Integer.toString(i);
            numbers.add(num);
            presentation.put(num, new TermSnapshotCollectedEvent.InterpellationPresentation(
                    "Interpelacja numer " + i + " z długim opisem testowym",
                    "https://sejm.example/interpelacja/" + i));
        }

        entity.onNewInterpellationsDetected(new TermSnapshotReconcilerEntity.NewInterpellationsDetectedEvent(
                10,
                List.copyOf(numbers),
                Map.copyOf(presentation)));

        var captor = ArgumentCaptor.forClass(String.class);
        verify(ownerNotifier, atLeast(2)).notifyOwner(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(message -> message.contains("- 1: Interpelacja numer 1"));
        assertThat(captor.getAllValues()).anyMatch(message -> message.contains("- 250: Interpelacja numer 250"));
        assertThat(captor.getAllValues()).allMatch(message -> message.length() <= 3900);
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