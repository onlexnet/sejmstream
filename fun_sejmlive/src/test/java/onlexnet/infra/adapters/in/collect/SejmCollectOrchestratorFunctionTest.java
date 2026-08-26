package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.TaskFailedException;
import com.microsoft.durabletask.TaskOptions;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.interruption.OrchestratorBlockedException;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.infra.adapters.in.azurefunc.CollectCoordinatorContractOperations;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequest;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResult;

class SejmCollectOrchestratorFunctionTest {

    private static final String COORDINATOR_ENTITY_NAME = "CollectCoordinator";
    private static final String COORDINATOR_ENTITY_KEY = "singleton";

    @Test
    void givenOrchestrator_whenInvoked_thenCallsActivitiesWithRetryAndAggregatesCounts() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var votingTask = SejmCollectFunctionTestSupport.completedTask(SejmCollectFunctionTestSupport.activityResult(1));
        var committeesTask = SejmCollectFunctionTestSupport.completedTask(SejmCollectFunctionTestSupport.activityResult(2));
        var printsTask = SejmCollectFunctionTestSupport.completedTask(SejmCollectFunctionTestSupport.activityResult(3));
        var interpellationsTask = SejmCollectFunctionTestSupport.completedTask(SejmCollectFunctionTestSupport.activityResult(4));
        var questionsTask = SejmCollectFunctionTestSupport.completedTask(SejmCollectFunctionTestSupport.activityResult(5));
        var billsTask = SejmCollectFunctionTestSupport.completedTask(SejmCollectFunctionTestSupport.activityResult(6));
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
        when(orchestrationContext.allOf(SejmCollectFunctionTestSupport.anyCollectActivityTasks()))
                .thenReturn(new com.microsoft.durabletask.CompletedTask<>(List.of(
                        SejmCollectFunctionTestSupport.activityResult(1),
                        SejmCollectFunctionTestSupport.activityResult(2),
                        SejmCollectFunctionTestSupport.activityResult(3),
                        SejmCollectFunctionTestSupport.activityResult(4),
                        SejmCollectFunctionTestSupport.activityResult(5),
                        SejmCollectFunctionTestSupport.activityResult(6))));

        var result = support.runOrchestrator(orchestrationContext);

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
    }

    @Test
    void givenUncompletedActivity_whenOrchestratorAwaits_thenPropagatesBlockedExceptionWithoutFailureSignal() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var blockedException = new OrchestratorBlockedException("activity is not completed");
        var activityTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResult(0));
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(activityTask);
        when(orchestrationContext.allOf(SejmCollectFunctionTestSupport.anyCollectActivityTasks()))
                .thenThrow(blockedException);

        assertThatThrownBy(() -> support.runOrchestrator(orchestrationContext))
                .isSameAs(blockedException);

        verify(orchestrationContext, never()).signalEntity(
                any(), eq(CollectCoordinatorContractOperations.COLLECT_FAILED.methodName()), any());
    }

    @Test
    void givenAllOfAwaitTaskFailure_whenOrchestratorRuns_thenSignalsFailureAndThrowsIllegalState() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var allOfFailure = mock(TaskFailedException.class);
        when(allOfFailure.getMessage()).thenReturn("allOf failed");
        var activityTask = SejmCollectFunctionTestSupport.completedTask(
                SejmCollectFunctionTestSupport.activityResult(0));
        when(orchestrationContext.getInstanceId()).thenReturn("collect-instance-2");
        when(orchestrationContext.callActivity(
                any(String.class),
                any(CollectActivityRequest.class),
                any(TaskOptions.class),
                eq(CollectActivityResult.class))).thenReturn(activityTask);
        when(orchestrationContext.allOf(SejmCollectFunctionTestSupport.anyCollectActivityTasks()))
                .thenThrow(allOfFailure);

        assertThatThrownBy(() -> support.runOrchestrator(orchestrationContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Collect orchestrator failed while waiting for activity completion");

        verify(orchestrationContext).signalEntity(
                any(),
                eq(CollectCoordinatorContractOperations.COLLECT_FAILED.methodName()),
                any());
    }
}
