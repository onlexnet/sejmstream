package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
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

import onlexnet.infra.adapters.in.azurefunc.collectorchestrator.CollectActivityResultWire;
import onlexnet.infra.adapters.in.azurefunc.collectorchestrator.SejmCollectOrchestratorFunction;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.collectcoordinator.CollectCoordinatorContractOperations;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequestDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectCompletedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectFailedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectEventPublishRequestDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInputDTO;
import onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotReconcilerContractOperations;
import onlexnet.shared.JsonDateNumbers;

class SejmCollectOrchestratorFunctionTest {

    private static final String COORDINATOR_ENTITY_NAME = "CollectCoordinator";
    private static final String COORDINATOR_ENTITY_KEY = "singleton";
    private static final String TERM_SNAPSHOT_ENTITY_NAME = "SejmTermSnapshot";

        private static CollectOrchestrationInputDTO validInput() {
                var input = new CollectOrchestrationInputDTO();
                input.setCoordinatorEntityId(new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY).toString());
                input.setSource("orchestrator");
                return input;
        }

    @Test
    void givenOrchestrator_whenInvoked_thenCallsActivitiesSequentiallyAndAggregatesCounts() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var collectionDate = LocalDate.of(2026, 8, 27);
                when(orchestrationContext.getInput(CollectOrchestrationInputDTO.class)).thenReturn(validInput());

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
        var publishEventTask = SejmCollectFunctionTestSupport.completedTask("published");

        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-1");
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_VOTINGS),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(votingTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_COMMITTEES),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(committeesTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_PRINTS),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(printsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_INTERPELLATIONS),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(interpellationsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_QUESTIONS),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(questionsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_BILLS),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(billsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_PUBLISH_COLLECT_EVENT),
                any(CollectEventPublishRequestDTO.class),
                any(TaskOptions.class),
                eq(String.class))).thenReturn(publishEventTask);

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
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class));
        verify(orchestrationContext).callActivity(
                eq(SejmCollectFunctions.ACTIVITY_PUBLISH_COLLECT_EVENT),
                argThat((CollectEventPublishRequestDTO request) ->
                        "collect-instance-1".equals(request.getOrchestrationInstanceId())
                                && "orchestrator".equals(request.getSource())
                                && Integer.valueOf(10).equals(request.getTermNum())
                                && Integer.valueOf(JsonDateNumbers.toYyyyMmDd(collectionDate))
                                        .equals(request.getCollectionDate())),
                any(TaskOptions.class),
                eq(String.class));
        verify(orchestrationContext).signalEntity(
                eq(new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY)),
                eq(CollectCoordinatorContractOperations.DISPATCH.methodName()),
                argThat(command -> isCompletedDispatchCommand(command, "collect-instance-1")));
        verify(orchestrationContext).signalEntity(
                eq(new EntityInstanceId(TERM_SNAPSHOT_ENTITY_NAME, "term10")),
                eq(TermSnapshotReconcilerContractOperations.TERM_SNAPSHOT_COLLECTED.methodName()),
                any());
    }

    @Test
    void givenUncompletedActivity_whenOrchestratorAwaits_thenPropagatesBlockedExceptionWithoutFailureSignal() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var blockedException = new OrchestratorBlockedException("activity is not completed");
                when(orchestrationContext.getInput(CollectOrchestrationInputDTO.class)).thenReturn(validInput());

        @SuppressWarnings("unchecked")
        Task<CollectActivityResultWire> activityTask = mock(Task.class);
        when(activityTask.await()).thenThrow(blockedException);
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(activityTask);

        var cancelEventTask = SejmCollectFunctionTestSupport.completedTask("unused");
        when(orchestrationContext.waitForExternalEvent("collect-cancel", String.class)).thenReturn(cancelEventTask);
        @SuppressWarnings("unchecked")
        Task<Task<?>> winnerTask = mock(Task.class);
        doReturn(activityTask).when(winnerTask).await();
        when(orchestrationContext.anyOf(org.mockito.ArgumentMatchers.<List<Task<?>>>any())).thenReturn(winnerTask);

        assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext)).isSameAs(blockedException);

        verify(orchestrationContext, never()).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.DISPATCH.methodName()),
                isA(CollectCoordinatorCollectFailedCommandDTO.class));
    }

    @Test
    void givenActivityFailure_whenOrchestratorRuns_thenSignalsFailureAndThrowsIllegalState() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var activityFailure = mock(TaskFailedException.class);
                when(orchestrationContext.getInput(CollectOrchestrationInputDTO.class)).thenReturn(validInput());

        when(activityFailure.getMessage()).thenReturn("activity failed");
        when(activityFailure.getErrorDetails()).thenReturn(null);

        @SuppressWarnings("unchecked")
        Task<CollectActivityResultWire> activityTask = mock(Task.class);
        when(activityTask.await()).thenThrow(activityFailure);

        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-2");
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(activityTask);

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
                eq(CollectCoordinatorContractOperations.DISPATCH.methodName()),
                argThat(command -> isFailedDispatchCommand(
                        command,
                        "collect-instance-2",
                        "Collect orchestrator failed in activity " + SejmCollectFunctions.ACTIVITY_VOTINGS)));
    }

    @Test
    void givenSnapshotSignalFails_whenOrchestratorRuns_thenSignalsFailureAndThrows() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var collectionDate = LocalDate.of(2026, 8, 27);
                when(orchestrationContext.getInput(CollectOrchestrationInputDTO.class)).thenReturn(validInput());
        var activityTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(1, 10, collectionDate, List.of("k"), java.util.Map.of("k", "fp")));

        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-3");
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(activityTask);

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
                        eq(TermSnapshotReconcilerContractOperations.TERM_SNAPSHOT_COLLECTED.methodName()),
                        any());

        assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snapshot signal failed");

        verify(orchestrationContext).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.DISPATCH.methodName()),
                argThat(command -> isFailedDispatchCommand(command, "collect-instance-3", "snapshot signal failed")));
    }

    @Test
    void givenFinalizationBlocked_whenOrchestratorRuns_thenPropagatesBlockedWithoutFailureSignal() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var collectionDate = LocalDate.of(2026, 8, 27);
                when(orchestrationContext.getInput(CollectOrchestrationInputDTO.class)).thenReturn(validInput());
        var activityTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(1, 10, collectionDate, List.of("k"), java.util.Map.of("k", "fp")));

        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-blocked-finalization");
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(activityTask);

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
                        eq(TermSnapshotReconcilerContractOperations.TERM_SNAPSHOT_COLLECTED.methodName()),
                        any());

        assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext)).isSameAs(blocked);

        verify(orchestrationContext, never()).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.DISPATCH.methodName()),
                isA(CollectCoordinatorCollectFailedCommandDTO.class));
    }

    @Test
    void givenCancelEventWinsRace_whenOrchestratorRuns_thenSignalsFailureAndThrows() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var activityTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResult(0));
                when(orchestrationContext.getInput(CollectOrchestrationInputDTO.class)).thenReturn(validInput());

        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-cancel");
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(activityTask);

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
                eq(CollectCoordinatorContractOperations.DISPATCH.methodName()),
                argThat(command -> isFailedDispatchCommand(command, "collect-instance-cancel", "collect-cancel")));
        verify(orchestrationContext, never()).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.DISPATCH.methodName()),
                isA(CollectCoordinatorCollectCompletedCommandDTO.class));
    }

    @Test
    void givenAnyOfReturnsWrapperTask_whenOrchestratorRuns_thenUsesOriginalActivityTaskAndSchedulesNextActivities() {
        var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var collectionDate = LocalDate.of(2026, 8, 27);
        when(orchestrationContext.getInput(CollectOrchestrationInputDTO.class)).thenReturn(validInput());

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
        var publishEventTask = SejmCollectFunctionTestSupport.completedTask("published");

        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-wrapper");
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_VOTINGS),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(votingTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_COMMITTEES),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(committeesTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_PRINTS),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(printsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_INTERPELLATIONS),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(interpellationsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_QUESTIONS),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(questionsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_BILLS),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(billsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_PUBLISH_COLLECT_EVENT),
                any(CollectEventPublishRequestDTO.class),
                any(TaskOptions.class),
                eq(String.class))).thenReturn(publishEventTask);

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
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class));
        verify(orchestrationContext).callActivity(
                eq(SejmCollectFunctions.ACTIVITY_PUBLISH_COLLECT_EVENT),
                any(CollectEventPublishRequestDTO.class),
                any(TaskOptions.class),
                eq(String.class));
    }

    @Test
    void givenPublishActivityFailure_whenOrchestratorRuns_thenSignalsFailureAndThrows() {
        var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var collectionDate = LocalDate.of(2026, 8, 27);
        when(orchestrationContext.getInput(CollectOrchestrationInputDTO.class)).thenReturn(validInput());
        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-publish-failure");

        var collectActivityTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResultWithSnapshot(1, 10, collectionDate, List.of("k"), java.util.Map.of("k", "fp")));
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(collectActivityTask);

        @SuppressWarnings("unchecked")
        Task<String> publishTask = mock(Task.class);
        var publishFailure = mock(TaskFailedException.class);
        when(publishFailure.getMessage()).thenReturn("event publish failed");
        when(publishFailure.getErrorDetails()).thenReturn(null);
        when(publishTask.await()).thenThrow(publishFailure);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_PUBLISH_COLLECT_EVENT),
                any(CollectEventPublishRequestDTO.class),
                any(TaskOptions.class),
                eq(String.class))).thenReturn(publishTask);

        var cancelEventTask = SejmCollectFunctionTestSupport.completedTask("unused");
        when(orchestrationContext.waitForExternalEvent("collect-cancel", String.class)).thenReturn(cancelEventTask);
        @SuppressWarnings("unchecked")
        Task<Task<?>> winnerTask = mock(Task.class);
        doReturn(collectActivityTask).when(winnerTask).await();
        when(orchestrationContext.anyOf(org.mockito.ArgumentMatchers.<List<Task<?>>>any())).thenReturn(winnerTask);

        assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Collect orchestrator failed in activity "
                        + SejmCollectFunctions.ACTIVITY_PUBLISH_COLLECT_EVENT);

        verify(orchestrationContext).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.DISPATCH.methodName()),
                argThat(command -> isFailedDispatchCommand(
                        command,
                        "collect-instance-publish-failure",
                        SejmCollectFunctions.ACTIVITY_PUBLISH_COLLECT_EVENT)));
    }

    @Test
        void givenAnyOfReturnsTaskOutsideCandidates_whenOrchestratorRuns_thenSignalsFailureAndThrows() {
        var orchestratorFunction = new SejmCollectOrchestratorFunction(SejmCollectFunctionTestSupport.newJsonValidator());
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        when(orchestrationContext.getInput(CollectOrchestrationInputDTO.class)).thenReturn(validInput());
        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-unexpected-winner");

        var votingTask = SejmCollectFunctionTestSupport.completedTask(SejmCollectFunctionTestSupport.activityResult(1));
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_VOTINGS),
                any(CollectActivityRequestDTO.class),
                any(TaskOptions.class),
                eq(CollectActivityResultWire.class))).thenReturn(votingTask);

        var cancelEventTask = SejmCollectFunctionTestSupport.completedTask("unused");
        when(orchestrationContext.waitForExternalEvent("collect-cancel", String.class)).thenReturn(cancelEventTask);

        Task<?> unknownWinnerTask = mock(Task.class);
        doReturn("not-a-CollectActivityResultDTO").when(unknownWinnerTask).await();
        @SuppressWarnings("unchecked")
        Task<Task<?>> winnerTask = mock(Task.class);
        doReturn(unknownWinnerTask).when(winnerTask).await();
        when(orchestrationContext.anyOf(org.mockito.ArgumentMatchers.<List<Task<?>>>any())).thenReturn(winnerTask);

        assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("winner task that was not an anyOf candidate");

        verify(orchestrationContext).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.DISPATCH.methodName()),
                argThat(command -> isFailedDispatchCommand(
                        command,
                        "collect-instance-unexpected-winner",
                        "winner task that was not an anyOf candidate")));
    }

        @Test
        void givenInvalidOrchestratorInput_whenOrchestratorRuns_thenSignalsFailureToDefaultCoordinator() {
                var orchestratorFunction = new SejmCollectOrchestratorFunction(
                                SejmCollectFunctionTestSupport.newJsonValidator());
                var orchestrationContext = mock(TaskOrchestrationContext.class);

                var invalidInput = new CollectOrchestrationInputDTO();
                invalidInput.setCoordinatorEntityId("");
                invalidInput.setSource("telegram-recovery");

                when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-invalid-input");
                when(orchestrationContext.getInput(CollectOrchestrationInputDTO.class)).thenReturn(invalidInput);

                assertThatThrownBy(() -> orchestratorFunction.runOrchestrator(orchestrationContext))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("collect-orchestration-input.schema.json");

                verify(orchestrationContext).signalEntity(
                                eq(new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY)),
                                                                eq(CollectCoordinatorContractOperations.DISPATCH.methodName()),
                                                                argThat(command -> isFailedDispatchCommand(
                                                                                command,
                                                                                "collect-instance-invalid-input",
                                                                                "collect-orchestration-input.schema.json")));
        }

        private static boolean isCompletedDispatchCommand(Object command, String expectedInstanceId) {
                if (!(command instanceof CollectCoordinatorCollectCompletedCommandDTO completedCommand)) {
                        return false;
                }
                var completion = completedCommand.getCompletion();
                return completion != null && expectedInstanceId.equals(completion.getOrchestrationInstanceId());
        }

        private static boolean isFailedDispatchCommand(Object command, String expectedInstanceId, String expectedMessageFragment) {
                if (!(command instanceof CollectCoordinatorCollectFailedCommandDTO failedCommand)) {
                        return false;
                }
                var failure = failedCommand.getFailure();
                if (failure == null) {
                        return false;
                }
                var message = failure.getMessage();
                return expectedInstanceId.equals(failure.getOrchestrationInstanceId())
                                && message != null
                                && message.contains(expectedMessageFragment);
        }
}
