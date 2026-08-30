package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.Task;
import com.microsoft.durabletask.TaskFailedException;
import com.microsoft.durabletask.TaskOptions;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.interruption.OrchestratorBlockedException;

import onlexnet.infra.adapters.in.azurefunc.SejmCollectOrchestratorFunction;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.collectCoordinator.CollectCoordinatorContractOperations;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequest;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResult;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInput;
import onlexnet.infra.adapters.in.azurefunc.sejmTermSnapshot.SejmTermSnapshotContractOperations;

class SejmCollectOrchestratorFunctionTest {

    private static final String COORDINATOR_ENTITY_NAME = "CollectCoordinator";
    private static final String COORDINATOR_ENTITY_KEY = "singleton";
    private static final String TERM_SNAPSHOT_ENTITY_NAME = "SejmTermSnapshot";

        private static CollectOrchestrationInput validInput() {
                var input = new CollectOrchestrationInput();
                input.setCoordinatorEntityId(new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY).toString());
                input.setSource("orchestrator");
                return input;
        }

    @Test
    void givenOrchestrator_whenInvoked_thenCallsActivitiesSequentiallyAndAggregatesCounts() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var collectionDate = LocalDate.of(2026, 8, 27);
                when(orchestrationContext.getInput(CollectOrchestrationInput.class)).thenReturn(validInput());

        var votingTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(1, 10, collectionDate, List.of(), java.util.Map.of()));
        var committeesTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(2, 10, collectionDate, List.of(), java.util.Map.of()));
        var printsTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(3, 10, collectionDate, List.of("401"), java.util.Map.of()));
        var interpellationsTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(4, 10, collectionDate, List.of("77"), java.util.Map.of("77", "abc")));
        var questionsTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(5, 10, collectionDate, List.of("301"), java.util.Map.of()));
        var billsTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(6, 10, collectionDate, List.of("501"), java.util.Map.of()));

        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-1");
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_VOTINGS),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(votingTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_COMMITTEES),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(committeesTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_PRINTS),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(printsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_INTERPELLATIONS),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(interpellationsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_QUESTIONS),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(questionsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_BILLS),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(billsTask);

        var cancelEventTask = SejmCollectFunctionTestSupport.completedTask("unused");
        when(orchestrationContext.waitForExternalEvent("collect-cancel", String.class)).thenReturn(cancelEventTask);
        when(orchestrationContext.anyOf(org.mockito.ArgumentMatchers.<List<Task<?>>>any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    var tasks = (List<Task<?>>) invocation.getArgument(0);
                    return SejmCollectFunctionTestSupport.completedTask(tasks.get(0));
                });

        var result = orchestratorFunction.runOrchestrator(orchestrationContext);

        assertThat(result.getCountsByType()).containsEntry("VOTING", 1);
        assertThat(result.getCountsByType()).containsEntry("COMMITTEE_SITTING", 2);
        assertThat(result.getCountsByType()).containsEntry("PRINT", 3);
        assertThat(result.getCountsByType()).containsEntry("INTERPELLATION", 4);
        assertThat(result.getCountsByType()).containsEntry("WRITTEN_QUESTION", 5);
        assertThat(result.getCountsByType()).containsEntry("BILL", 6);

        verify(orchestrationContext, times(6)).callActivity(
                any(String.class),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class));
        verify(orchestrationContext).signalEntity(
                eq(new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY)),
                eq(CollectCoordinatorContractOperations.COLLECT_COMPLETED.methodName()),
                any());
        verify(orchestrationContext).signalEntity(
                eq(new EntityInstanceId(TERM_SNAPSHOT_ENTITY_NAME, "term10")),
                eq(SejmTermSnapshotContractOperations.TERM_SNAPSHOT_COLLECTED.methodName()),
                any());
    }

    @Test
    void givenUncompletedActivity_whenOrchestratorAwaits_thenPropagatesBlockedExceptionWithoutFailureSignal() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var blockedException = new OrchestratorBlockedException("activity is not completed");
                when(orchestrationContext.getInput(CollectOrchestrationInput.class)).thenReturn(validInput());

        @SuppressWarnings("unchecked")
        Task<CollectActivityResult> activityTask = mock(Task.class);
        when(activityTask.await()).thenThrow(blockedException);
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(activityTask);

        var cancelEventTask = SejmCollectFunctionTestSupport.completedTask("unused");
        when(orchestrationContext.waitForExternalEvent("collect-cancel", String.class)).thenReturn(cancelEventTask);
        @SuppressWarnings("unchecked")
        Task<Task<?>> winnerTask = mock(Task.class);
        doReturn(activityTask).when(winnerTask).await();
        when(orchestrationContext.anyOf(org.mockito.ArgumentMatchers.<List<Task<?>>>any())).thenReturn(winnerTask);

        assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext)).isSameAs(blockedException);

        verify(orchestrationContext, never()).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.COLLECT_FAILED.methodName()),
                any());
    }

    @Test
    void givenActivityFailure_whenOrchestratorRuns_thenSignalsFailureAndThrowsIllegalState() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var activityFailure = mock(TaskFailedException.class);
                when(orchestrationContext.getInput(CollectOrchestrationInput.class)).thenReturn(validInput());

        when(activityFailure.getMessage()).thenReturn("activity failed");
        when(activityFailure.getErrorDetails()).thenReturn(null);

        @SuppressWarnings("unchecked")
        Task<CollectActivityResult> activityTask = mock(Task.class);
        when(activityTask.await()).thenThrow(activityFailure);

        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-2");
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(activityTask);

        var cancelEventTask = SejmCollectFunctionTestSupport.completedTask("unused");
        when(orchestrationContext.waitForExternalEvent("collect-cancel", String.class)).thenReturn(cancelEventTask);
        @SuppressWarnings("unchecked")
        Task<Task<?>> winnerTask = mock(Task.class);
        doReturn(activityTask).when(winnerTask).await();
        when(orchestrationContext.anyOf(org.mockito.ArgumentMatchers.<List<Task<?>>>any())).thenReturn(winnerTask);

        assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Collect orchestrator failed in activity " + SejmCollectFunctions.ACTIVITY_VOTINGS);

        verify(orchestrationContext).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.COLLECT_FAILED.methodName()),
                any());
    }

    @Test
    void givenSnapshotSignalFails_whenOrchestratorRuns_thenSignalsFailureAndThrows() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var collectionDate = LocalDate.of(2026, 8, 27);
                when(orchestrationContext.getInput(CollectOrchestrationInput.class)).thenReturn(validInput());
        var activityTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(1, 10, collectionDate, List.of("k"), java.util.Map.of("k", "fp")));

        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-3");
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(activityTask);

        var cancelEventTask = SejmCollectFunctionTestSupport.completedTask("unused");
        when(orchestrationContext.waitForExternalEvent("collect-cancel", String.class)).thenReturn(cancelEventTask);
        @SuppressWarnings("unchecked")
        Task<Task<?>> winnerTask = mock(Task.class);
        doReturn(activityTask).when(winnerTask).await();
        when(orchestrationContext.anyOf(org.mockito.ArgumentMatchers.<List<Task<?>>>any())).thenReturn(winnerTask);

        org.mockito.Mockito.doThrow(new IllegalStateException("snapshot signal failed"))
                .when(orchestrationContext)
                .signalEntity(
                        eq(new EntityInstanceId(TERM_SNAPSHOT_ENTITY_NAME, "term10")),
                        eq(SejmTermSnapshotContractOperations.TERM_SNAPSHOT_COLLECTED.methodName()),
                        any());

        assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snapshot signal failed");

        verify(orchestrationContext).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.COLLECT_FAILED.methodName()),
                any());
    }

    @Test
    void givenFinalizationBlocked_whenOrchestratorRuns_thenPropagatesBlockedWithoutFailureSignal() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var collectionDate = LocalDate.of(2026, 8, 27);
                when(orchestrationContext.getInput(CollectOrchestrationInput.class)).thenReturn(validInput());
        var activityTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(1, 10, collectionDate, List.of("k"), java.util.Map.of("k", "fp")));

        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-blocked-finalization");
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(activityTask);

        var cancelEventTask = SejmCollectFunctionTestSupport.completedTask("unused");
        when(orchestrationContext.waitForExternalEvent("collect-cancel", String.class)).thenReturn(cancelEventTask);
        @SuppressWarnings("unchecked")
        Task<Task<?>> winnerTask = mock(Task.class);
        doReturn(activityTask).when(winnerTask).await();
        when(orchestrationContext.anyOf(org.mockito.ArgumentMatchers.<List<Task<?>>>any())).thenReturn(winnerTask);

        var blocked = new OrchestratorBlockedException("yield after finalization");
        org.mockito.Mockito.doThrow(blocked)
                .when(orchestrationContext)
                .signalEntity(
                        eq(new EntityInstanceId(TERM_SNAPSHOT_ENTITY_NAME, "term10")),
                        eq(SejmTermSnapshotContractOperations.TERM_SNAPSHOT_COLLECTED.methodName()),
                        any());

        assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext)).isSameAs(blocked);

        verify(orchestrationContext, never()).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.COLLECT_FAILED.methodName()),
                any());
    }

    @Test
    void givenCancelEventWinsRace_whenOrchestratorRuns_thenSignalsFailureAndThrows() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var activityTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResult(0));
                when(orchestrationContext.getInput(CollectOrchestrationInput.class)).thenReturn(validInput());

        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-cancel");
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(activityTask);

        var cancelEventTask = SejmCollectFunctionTestSupport.completedTask("manual-stop");
        when(orchestrationContext.waitForExternalEvent("collect-cancel", String.class)).thenReturn(cancelEventTask);
        @SuppressWarnings("unchecked")
        Task<Task<?>> winnerCancelTask = mock(Task.class);
        doReturn(cancelEventTask).when(winnerCancelTask).await();
        when(orchestrationContext.anyOf(org.mockito.ArgumentMatchers.<List<Task<?>>>any())).thenReturn(winnerCancelTask);

        assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Collect orchestrator cancelled by external event 'collect-cancel'");

        verify(orchestrationContext).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.COLLECT_FAILED.methodName()),
                any());
        verify(orchestrationContext, never()).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.COLLECT_COMPLETED.methodName()),
                any());
    }

    @Test
    void givenAnyOfReturnsWrapperTask_whenOrchestratorRuns_thenUsesOriginalActivityTaskAndSchedulesNextActivities() {
        var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var collectionDate = LocalDate.of(2026, 8, 27);
        when(orchestrationContext.getInput(CollectOrchestrationInput.class)).thenReturn(validInput());

        var votingTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(1, 10, collectionDate, List.of(), java.util.Map.of()));
        var committeesTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(2, 10, collectionDate, List.of(), java.util.Map.of()));
        var printsTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(3, 10, collectionDate, List.of("401"), java.util.Map.of()));
        var interpellationsTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(4, 10, collectionDate, List.of("77"), java.util.Map.of("77", "abc")));
        var questionsTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(5, 10, collectionDate, List.of("301"), java.util.Map.of()));
        var billsTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(6, 10, collectionDate, List.of("501"), java.util.Map.of()));

        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-wrapper");
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_VOTINGS),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(votingTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_COMMITTEES),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(committeesTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_PRINTS),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(printsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_INTERPELLATIONS),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(interpellationsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_QUESTIONS),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(questionsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_BILLS),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(billsTask);

        var cancelEventTask = SejmCollectFunctionTestSupport.completedTask("unused");
        when(orchestrationContext.waitForExternalEvent("collect-cancel", String.class)).thenReturn(cancelEventTask);

        var activityTasks = List.of(votingTask, committeesTask, printsTask, interpellationsTask, questionsTask, billsTask);
        var nextTask = new AtomicInteger(0);
        when(orchestrationContext.anyOf(org.mockito.ArgumentMatchers.<List<Task<?>>>any())).thenAnswer(invocation -> {
            var wrappedWinner = activityTasks.get(nextTask.getAndIncrement());
            return SejmCollectFunctionTestSupport.completedTask((Task<?>) wrappedWinner);
        });

        var result = orchestratorFunction.runOrchestrator(orchestrationContext);

        assertThat(result.getCountsByType()).containsEntry("VOTING", 1);
        assertThat(result.getCountsByType()).containsEntry("COMMITTEE_SITTING", 2);
        assertThat(result.getCountsByType()).containsEntry("PRINT", 3);
        assertThat(result.getCountsByType()).containsEntry("INTERPELLATION", 4);
        assertThat(result.getCountsByType()).containsEntry("WRITTEN_QUESTION", 5);
        assertThat(result.getCountsByType()).containsEntry("BILL", 6);

        verify(orchestrationContext, times(6)).callActivity(
                any(String.class),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class));
    }

    @Test
    void givenAnyOfReturnsUnexpectedWinner_whenOrchestratorRuns_thenSignalsFailureAndThrows() {
        var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        when(orchestrationContext.getInput(CollectOrchestrationInput.class)).thenReturn(validInput());
        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-unexpected-winner");

        var votingTask = SejmCollectFunctionTestSupport.completedTask(SejmCollectFunctionTestSupport.activityResult(1));
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_VOTINGS),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(votingTask);

        var cancelEventTask = SejmCollectFunctionTestSupport.completedTask("unused");
        when(orchestrationContext.waitForExternalEvent("collect-cancel", String.class)).thenReturn(cancelEventTask);

        @SuppressWarnings("unchecked")
        Task<?> unknownWinnerTask = mock(Task.class);
        @SuppressWarnings("unchecked")
        Task<Task<?>> winnerTask = mock(Task.class);
        doReturn(unknownWinnerTask).when(winnerTask).await();
        when(orchestrationContext.anyOf(org.mockito.ArgumentMatchers.<List<Task<?>>>any())).thenReturn(winnerTask);

        assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpected winner task from anyOf");

        verify(orchestrationContext).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.COLLECT_FAILED.methodName()),
                any());
    }

        @Test
        void givenInvalidOrchestratorInput_whenOrchestratorRuns_thenSignalsFailureToDefaultCoordinator() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(
                                SejmCollectFunctionTestSupport.newJsonValidator());
                var orchestrationContext = mock(TaskOrchestrationContext.class);

                var invalidInput = new CollectOrchestrationInput();
                invalidInput.setCoordinatorEntityId("");
                invalidInput.setSource("telegram-recovery");

                when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-invalid-input");
                when(orchestrationContext.getInput(CollectOrchestrationInput.class)).thenReturn(invalidInput);

                assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("collect-orchestration-input.schema.json");

                verify(orchestrationContext).signalEntity(
                                eq(new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY)),
                                eq(CollectCoordinatorContractOperations.COLLECT_FAILED.methodName()),
                                any());
        }
}
