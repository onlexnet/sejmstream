package onlexnet.infra.adapters.in.azurefunc.collectorchestrator;

import static onlexnet.infra.adapters.in.azurefunc.collectcoordinator.CollectCoordinatorContractOperations.DISPATCH;
import static onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotReconcilerContractOperations.TERM_SNAPSHOT_COLLECTED;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.RetryPolicy;
import com.microsoft.durabletask.Task;
import com.microsoft.durabletask.TaskFailedException;
import com.microsoft.durabletask.TaskOptions;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;
import com.microsoft.durabletask.interruption.OrchestratorBlockedException;

import lombok.RequiredArgsConstructor;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequestDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResultDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletionDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectCompletedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectFailedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectEventPublishRequestDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectFailureDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInputDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectResultDTO;
import onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotCollectedEvent;
import onlexnet.shared.JsonDateNumbers;

@Component
@RequiredArgsConstructor
public final class SejmCollectOrchestratorFunction {

    private static final String COLLECT_CANCEL_EVENT_NAME = "collect-cancel";

    private static final EntityInstanceId COLLECT_COORDINATOR_ENTITY_ID =
            new EntityInstanceId(SejmCollectFunctions.COORDINATOR_ENTITY_NAME, SejmCollectFunctions.COORDINATOR_ENTITY_KEY);

    private final JsonValidator jsonValidator;

    @FunctionName(SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME)
    public CollectResultDTO runOrchestrator(
            @DurableOrchestrationTrigger(name = "orchestrationContext") TaskOrchestrationContext orchestrationContext) {
        return runOrchestratorInter(new OrchestrationContext(orchestrationContext));
    }

    CollectResultDTO runOrchestratorInter(OrchestrationContext ctx) {
        EntityInstanceId coordinatorEntityId = COLLECT_COORDINATOR_ENTITY_ID;
        String activitySource;

        try {
            var input = this.jsonValidator.validateReceived(
                    JsonValidator.COLLECT_ORCHESTRATION_INPUT,
                    ctx.getInput(CollectOrchestrationInputDTO.class));
            coordinatorEntityId = EntityInstanceId.fromString(input.getCoordinatorEntityId());
            activitySource = input.getSource();
        } catch (RuntimeException e) {
            signalCollectFailed(ctx, coordinatorEntityId, e);
            throw e;
        }

        var cancelRequestedTask = ctx.waitForExternalEvent(COLLECT_CANCEL_EVENT_NAME, String.class);

        var votingTask = startActivityWithRetry(ctx, SejmCollectFunctions.ACTIVITY_VOTINGS, activitySource);
        var votingResult = awaitActivityOrCancel(
                ctx,
                coordinatorEntityId,
                cancelRequestedTask,
                votingTask,
                SejmCollectFunctions.ACTIVITY_VOTINGS);

        var committeesTask =
                startActivityWithRetry(ctx, SejmCollectFunctions.ACTIVITY_COMMITTEES, activitySource);
        var committeesResult = awaitActivityOrCancel(
                ctx,
                coordinatorEntityId,
                cancelRequestedTask,
                committeesTask,
                SejmCollectFunctions.ACTIVITY_COMMITTEES);

        var printsTask = startActivityWithRetry(ctx, SejmCollectFunctions.ACTIVITY_PRINTS, activitySource);
        var printsResult = awaitActivityOrCancel(
                ctx,
                coordinatorEntityId,
                cancelRequestedTask,
                printsTask,
                SejmCollectFunctions.ACTIVITY_PRINTS);

        var interpellationsTask = startActivityWithRetry(
                ctx,
                SejmCollectFunctions.ACTIVITY_INTERPELLATIONS,
                activitySource);
        var interpellationsResult = awaitActivityOrCancel(
                ctx,
                coordinatorEntityId,
                cancelRequestedTask,
                interpellationsTask,
                SejmCollectFunctions.ACTIVITY_INTERPELLATIONS);

        var questionsTask = startActivityWithRetry(ctx, SejmCollectFunctions.ACTIVITY_QUESTIONS, activitySource);
        var questionsResult = awaitActivityOrCancel(
                ctx,
                coordinatorEntityId,
                cancelRequestedTask,
                questionsTask,
                SejmCollectFunctions.ACTIVITY_QUESTIONS);

        var billsTask = startActivityWithRetry(ctx, SejmCollectFunctions.ACTIVITY_BILLS, activitySource);
        var billsResult = awaitActivityOrCancel(
                ctx,
                coordinatorEntityId,
                cancelRequestedTask,
                billsTask,
                SejmCollectFunctions.ACTIVITY_BILLS);

        try {
            var counts = new HashMap<String, Integer>();
            counts.put("VOTING", requireCount(votingResult));
            counts.put("COMMITTEE_SITTING", requireCount(committeesResult));
            counts.put("PRINT", requireCount(printsResult));
            counts.put("INTERPELLATION", requireCount(interpellationsResult));
            counts.put("WRITTEN_QUESTION", requireCount(questionsResult));
            counts.put("BILL", requireCount(billsResult));

            var snapshotTermNum = requireSnapshotTermNum(interpellationsResult);
            var snapshotDate = requireSnapshotDate(interpellationsResult);

            reconcileTermSnapshot(ctx, activitySource, interpellationsResult, questionsResult, printsResult, billsResult);
            publishCollectEvent(ctx, activitySource, snapshotTermNum, snapshotDate, counts);

            var result = new CollectResultDTO();
            result.setCountsByType(Collections.unmodifiableMap(new HashMap<>(counts)));
            this.jsonValidator.validateToSend(JsonValidator.COLLECT_RESULT, result);
            var completion = new CollectCompletionDTO();
            completion.setOrchestrationInstanceId(ctx.getInstanceId());
            this.jsonValidator.validateToSend(JsonValidator.COLLECT_COMPLETION, completion);
            var completionCommand = new CollectCoordinatorCollectCompletedCommandDTO();
            completionCommand.setCompletion(completion);
            ctx.signalEntity(coordinatorEntityId, DISPATCH.methodName(), completionCommand);
            return result;
        } catch (OrchestratorBlockedException e) {
            throw e;
        } catch (RuntimeException e) {
            signalCollectFailed(ctx, coordinatorEntityId, e);
            throw e;
        }
    }

    private Task<CollectActivityResultWire> startActivityWithRetry(
            OrchestrationContext orchestrationContext,
            String activityName,
            String source) {
        var request = new CollectActivityRequestDTO();
        request.setSource(source);
        this.jsonValidator.validateToSend(JsonValidator.COLLECT_ACTIVITY_REQUEST, request);
        return orchestrationContext.callActivity(
                activityName,
                request,
                activityRetryOptions(),
                CollectActivityResultWire.class);
    }

    private static TaskOptions activityRetryOptions() {
        return new TaskOptions(
                new RetryPolicy(3, Duration.ofSeconds(10))
                        .setBackoffCoefficient(2.0)
                        .setMaxRetryInterval(Duration.ofMinutes(2))
                        .setRetryTimeout(Duration.ofMinutes(10)));
    }

    private void publishCollectEvent(
            OrchestrationContext orchestrationContext,
            String source,
            int termNum,
            int collectionDate,
            Map<String, Integer> countsByType) {
        var request = new CollectEventPublishRequestDTO();
        request.setOrchestrationInstanceId(orchestrationContext.getInstanceId());
        request.setSource(source);
        request.setTermNum(termNum);
        request.setCollectionDate(collectionDate);
        request.setCountsByType(Map.copyOf(countsByType));
        this.jsonValidator.validateToSend(JsonValidator.COLLECT_EVENT_PUBLISH_REQUEST, request);

        var publishTask = orchestrationContext.callActivity(
                SejmCollectFunctions.ACTIVITY_PUBLISH_COLLECT_EVENT,
                request,
                activityRetryOptions(),
                String.class);
        awaitPublishWithFailureContext(publishTask);
    }

    private static void awaitPublishWithFailureContext(Task<String> publishTask) {
        try {
            publishTask.await();
        } catch (TaskFailedException e) {
            var details = e.getErrorDetails();
            var errorType = details == null ? "unknown" : details.getErrorType();
            var errorMessage = details == null ? e.getMessage() : details.getErrorMessage();
            throw new IllegalStateException(
                    "Collect orchestrator failed in activity "
                            + SejmCollectFunctions.ACTIVITY_PUBLISH_COLLECT_EVENT
                            + " ("
                            + errorType
                            + "): "
                            + errorMessage,
                    e);
        }
    }

    private CollectActivityResultDTO awaitActivityWithFailureContext(Task<CollectActivityResultWire> task, String activityName) {
        try {
            var activityResultWire = task.await();
            if (activityResultWire == null) {
                throw new IllegalStateException("Collect orchestrator received null result from activity " + activityName);
            }
            return this.jsonValidator.validateReceived(
                    JsonValidator.COLLECT_ACTIVITY_RESULT,
                    activityResultWire.toSchemaModel());
        } catch (TaskFailedException e) {
            var details = e.getErrorDetails();
            var errorType = details == null ? "unknown" : details.getErrorType();
            var errorMessage = details == null ? e.getMessage() : details.getErrorMessage();
            throw new IllegalStateException(
                    "Collect orchestrator failed in activity " + activityName + " (" + errorType + "): " + errorMessage,
                    e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Collect orchestrator received invalid payload from activity " + activityName + ": " + e.getMessage(),
                    e);
        }
    }

    private CollectActivityResultDTO awaitActivityOrCancel(
            OrchestrationContext orchestrationContext,
            EntityInstanceId coordinatorEntityId,
            Task<String> cancelRequestedTask,
            Task<CollectActivityResultWire> activityTask,
            String activityName) {
        var firstCompletedTask = orchestrationContext.anyOf(List.of(activityTask, cancelRequestedTask)).await();

        if (firstCompletedTask != activityTask && firstCompletedTask != cancelRequestedTask) {
            var failure = new IllegalStateException(
                    "Collect orchestrator received a winner task that was not an anyOf candidate for activity "
                            + activityName);
            signalCollectFailed(orchestrationContext, coordinatorEntityId, failure);
            throw failure;
        }

        if (firstCompletedTask == cancelRequestedTask) {
            var cancelReason = cancelRequestedTask.await();
            var normalizedCancelReason =
                    cancelReason == null || cancelReason.isBlank() ? "no-reason-provided" : cancelReason;
            var cancellationFailure = new IllegalStateException(
                    "Collect orchestrator cancelled by external event '" + COLLECT_CANCEL_EVENT_NAME + "': "
                            + normalizedCancelReason);
            signalCollectFailed(orchestrationContext, coordinatorEntityId, cancellationFailure);
            throw cancellationFailure;
        }

        try {
            @SuppressWarnings("unchecked")
            var completedActivityTask = (Task<CollectActivityResultWire>) firstCompletedTask;
            return awaitActivityWithFailureContext(completedActivityTask, activityName);
        } catch (ClassCastException e) {
            var failure = new IllegalStateException(
                    "Collect orchestrator received unexpected winner task type for activity " + activityName,
                    e);
            signalCollectFailed(orchestrationContext, coordinatorEntityId, failure);
            throw failure;
        } catch (IllegalStateException e) {
            signalCollectFailed(orchestrationContext, coordinatorEntityId, e);
            throw e;
        }
    }

    private void reconcileTermSnapshot(
            OrchestrationContext orchestrationContext,
            String activitySource,
            CollectActivityResultDTO interpellationsResult,
            CollectActivityResultDTO questionsResult,
            CollectActivityResultDTO printsResult,
            CollectActivityResultDTO billsResult) {
        var termNum = requireSnapshotTermNum(interpellationsResult);
        var date = requireSnapshotDate(interpellationsResult);
        var event = new TermSnapshotCollectedEvent(
                date,
                activitySource,
                orchestrationContext.getInstanceId(),
                Map.copyOf(orEmptyMap(interpellationsResult.getInterpellationFingerprints())),
                List.copyOf(orEmptyList(questionsResult.getItemKeys())),
                List.copyOf(orEmptyList(printsResult.getItemKeys())),
                List.copyOf(orEmptyList(billsResult.getItemKeys())));
        orchestrationContext.signalEntity(
                termSnapshotEntityId(termNum),
                TERM_SNAPSHOT_COLLECTED.methodName(),
                event);
    }

    private static EntityInstanceId termSnapshotEntityId(int termNum) {
        return new EntityInstanceId(SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME, "term" + termNum);
    }

    private static int requireCount(CollectActivityResultDTO result) {
        return Objects.requireNonNull(result.getCount(), "Activity result count must not be null");
    }

    private static int requireSnapshotTermNum(CollectActivityResultDTO result) {
        return Objects.requireNonNull(result.getTermNum(), "Activity result termNum must not be null");
    }

    private static int requireSnapshotDate(CollectActivityResultDTO result) {
        var collectionDate = Objects.requireNonNull(result.getCollectionDate(), "Activity result collectionDate must not be null");
        JsonDateNumbers.fromYyyyMmDd(collectionDate);
        return collectionDate;
    }

    private static List<String> orEmptyList(@Nullable List<String> value) {
        return value == null ? List.of() : value;
    }

    private static Map<String, String> orEmptyMap(@Nullable Map<String, String> value) {
        return value == null ? Map.of() : value;
    }

    private static String orchestrationFailureMessage(RuntimeException exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private void signalCollectFailed(
            OrchestrationContext orchestrationContext,
            EntityInstanceId coordinatorEntityId,
            RuntimeException exception) {
        var failure = new CollectFailureDTO();
        failure.setOrchestrationInstanceId(orchestrationContext.getInstanceId());
        failure.setMessage(orchestrationFailureMessage(exception));
        this.jsonValidator.validateToSend(JsonValidator.COLLECT_FAILURE, failure);
        var failureCommand = new CollectCoordinatorCollectFailedCommandDTO();
        failureCommand.setFailure(failure);
        orchestrationContext.signalEntity(coordinatorEntityId, DISPATCH.methodName(), failureCommand);
    }
}