package onlexnet.infra.adapters.in.collect;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.AbstractTaskEntity;
import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.EntityRunner;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.durabletask.RetryPolicy;
import com.microsoft.durabletask.TaskFailedException;
import com.microsoft.durabletask.NewOrchestrationInstanceOptions;
import com.microsoft.durabletask.TaskOptions;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;
import com.microsoft.durabletask.azurefunctions.DurableEntityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.app.usecases.CollectCoordinatorDecider;

/**
 * Azure Durable Functions workflow that collects daily Sejm activity into the database.
 * Implements a 6-activity orchestrator pattern that sequentially collects votings, committee
 * sittings, prints, interpellations, written questions, and bills. Triggered via timer every hour
 * or manually via HTTP POST. Results are persisted in the daily digest tables.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public final class SejmCollectFunctions {

    /** Timer trigger function name. */
        public static final String TIMER_FUNCTION_NAME = "Fun_SejmCollectTimer";
    /** HTTP starter function name for manual trigger. */
        public static final String HTTP_STARTER_FUNCTION_NAME = "Fun_CollectStart";
    /** Durable orchestrator function name. */
        public static final String ORCHESTRATOR_FUNCTION_NAME = "Fun_CollectOrchestrator";
    /** Durable entity function name coordinating collect runs. */
        public static final String COORDINATOR_ENTITY_FUNCTION_NAME = "Fun_CollectCoordinatorEntity";
    /** Durable entity logical name used by the runtime. */
        static final String COORDINATOR_ENTITY_NAME = "CollectCoordinator";
    /** Durable entity singleton key for collect coordination. */
        static final String COORDINATOR_ENTITY_KEY = "singleton";
    /** Activity function name for collecting votings. */
        public static final String ACTIVITY_VOTINGS = "Intern_CollectVotings";
    /** Activity function name for collecting committee sittings. */
        public static final String ACTIVITY_COMMITTEES = "Intern_CollectCommittees";
    /** Activity function name for collecting prints. */
        public static final String ACTIVITY_PRINTS = "Intern_CollectPrints";
    /** Activity function name for collecting interpellations. */
        public static final String ACTIVITY_INTERPELLATIONS = "Intern_CollectInterpellations";
    /** Activity function name for collecting written questions. */
        public static final String ACTIVITY_QUESTIONS = "Intern_CollectQuestions";
    /** Activity function name for collecting bills. */
        public static final String ACTIVITY_BILLS = "Intern_CollectBills";
    /** Retry options for all orchestration activity calls. */
        // Helps absorb transient API/network/db glitches before failing the whole orchestration.
        private static final TaskOptions ACTIVITY_RETRY_OPTIONS = new TaskOptions(
                new RetryPolicy(3, Duration.ofSeconds(10))
                        .setBackoffCoefficient(2.0)
                        .setMaxRetryInterval(Duration.ofMinutes(2))
                        .setRetryTimeout(Duration.ofMinutes(10)));

    private final SejmCollectOperations collectService;
    private final SejmApiClient sejmApiClient;
    private CachedTerm cachedTermNum = CachedTerm.NONE;

        private static final String ENTITY_OPERATION_REQUEST_COLLECT = "requestCollect";
        private static final String ENTITY_OPERATION_COLLECT_COMPLETED = "collectCompleted";
        private static final String ENTITY_OPERATION_COLLECT_FAILED = "collectFailed";
        private static final EntityInstanceId COLLECT_COORDINATOR_ENTITY_ID =
            new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY);

    /** Holds the cached current Sejm term number, or {@link None} if not yet resolved. */
    private sealed interface CachedTerm permits CachedTerm.None, CachedTerm.Resolved {
        /** Sentinel representing an unresolved term. */
        enum None implements CachedTerm { NONE }
        /** A successfully resolved term number. */
        record Resolved(int num) implements CachedTerm {}

        CachedTerm NONE = None.NONE;
    }

    /**
     * Timer trigger that starts the collection orchestrator at the top of every hour.
     *
     * @param timerInfo        timer trigger information
     * @param durableContext   durable client context
     * @param executionContext Azure Functions execution context
     */
    @FunctionName(TIMER_FUNCTION_NAME)
    public void runTimer(
            @TimerTrigger(name = "timer", schedule = "0 0 * * * *")
            final String timerInfo,
            @DurableClientInput(name = "durableContext")
            final DurableClientContext durableContext,
            final ExecutionContext executionContext) {

        try {
            var instanceId = enqueueCollectRequest(durableContext, "timer");
            executionContext.getLogger().info(
                    "Collect request accepted from timer, instanceId=" + instanceId);
            log.debug("Collect request from timer accepted, instanceId={}", instanceId);
        } catch (Exception e) {
            log.error("Failed to enqueue collect request", e);
            executionContext.getLogger().severe("Error enqueueing collect request: " + e.getMessage());
            throw new IllegalStateException("Failed to enqueue collection request", e);
        }
    }

    /**
     * HTTP POST trigger for manually starting the collection orchestrator.
     * Useful for testing or triggering collection outside the scheduled time.
     *
     * @param request        incoming HTTP request
     * @param durableContext durable client context
     * @return HTTP 202 response with status endpoints
     */
    @FunctionName(HTTP_STARTER_FUNCTION_NAME)
    public HttpResponseMessage httpStart(
            @HttpTrigger(name = "request", methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION)
            final HttpRequestMessage<Optional<String>> request,
            @DurableClientInput(name = "durableContext")
            final DurableClientContext durableContext,
            final ExecutionContext executionContext) {

        try {
            var instanceId = enqueueCollectRequest(durableContext, "http");
            executionContext.getLogger().info(
                "Manual collect request accepted, instanceId=" + instanceId);
            log.debug("Manual collect request accepted, instanceId={}", instanceId);
            return request.createResponseBuilder(com.microsoft.azure.functions.HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "accepted", true,
                            "coordinatorEntityId", instanceId,
                            "message", "Collect request was enqueued for serialized processing"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to enqueue collect request via HTTP", e);
            executionContext.getLogger().severe(
                "Failed to enqueue collect request via HTTP: " + e.getMessage());
            return request.createResponseBuilder(com.microsoft.azure.functions.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to enqueue collection request: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Durable orchestrator that calls all 6 collection activities sequentially and aggregates results.
     * Activities are executed in order: votings, committees, prints, interpellations, questions, bills.
     *
     * @param orchestrationContext durable orchestration context
     * @return result with counts by data type
     */
    @FunctionName(ORCHESTRATOR_FUNCTION_NAME)
    public CollectResult runOrchestrator(
            @DurableOrchestrationTrigger(name = "orchestrationContext")
            final TaskOrchestrationContext orchestrationContext) {
        var input = orchestrationContext.getInput(CollectOrchestrationInput.class);
        var coordinatorEntityId = input == null
                ? COLLECT_COORDINATOR_ENTITY_ID
                : EntityInstanceId.fromString(input.coordinatorEntityId());

        try {
            var counts = new HashMap<String, Integer>();

            counts.put("VOTING", callActivityWithRetry(orchestrationContext, ACTIVITY_VOTINGS));
            counts.put("COMMITTEE_SITTING",
                callActivityWithRetry(orchestrationContext, ACTIVITY_COMMITTEES));
            counts.put("PRINT", callActivityWithRetry(orchestrationContext, ACTIVITY_PRINTS));
            counts.put("INTERPELLATION",
                callActivityWithRetry(orchestrationContext, ACTIVITY_INTERPELLATIONS));
            counts.put("WRITTEN_QUESTION",
                callActivityWithRetry(orchestrationContext, ACTIVITY_QUESTIONS));
            counts.put("BILL", callActivityWithRetry(orchestrationContext, ACTIVITY_BILLS));

            var result = new CollectResult(Map.copyOf(counts));
            orchestrationContext.signalEntity(
                    coordinatorEntityId,
                    ENTITY_OPERATION_COLLECT_COMPLETED,
                    new CollectCompletion(orchestrationContext.getInstanceId()));
            return result;
        } catch (RuntimeException e) {
            orchestrationContext.signalEntity(
                    coordinatorEntityId,
                    ENTITY_OPERATION_COLLECT_FAILED,
                    new CollectFailure(orchestrationContext.getInstanceId(), e.getMessage()));
            throw e;
        }
    }

    @FunctionName(COORDINATOR_ENTITY_FUNCTION_NAME)
    public String runCollectCoordinatorEntity(
            @DurableEntityTrigger(name = "entityRequest", entityName = COORDINATOR_ENTITY_NAME)
            final String entityRequest,
            final ExecutionContext executionContext) {
        executionContext.getLogger().info("Processing collect coordinator entity batch");
        return EntityRunner.loadAndRun(entityRequest, CollectCoordinatorEntity::new);
    }

    private static String enqueueCollectRequest(final DurableClientContext durableContext, final String source) {
        var client = durableContext.getClient().getEntities();
        client.signalEntity(COLLECT_COORDINATOR_ENTITY_ID, ENTITY_OPERATION_REQUEST_COLLECT, source);
        return COLLECT_COORDINATOR_ENTITY_ID.toString();
    }

    private int callActivityWithRetry(final TaskOrchestrationContext orchestrationContext,
            final String activityName) {
        try {
            return orchestrationContext
                    .callActivity(activityName, null, ACTIVITY_RETRY_OPTIONS, Integer.class)
                    .await();
        } catch (TaskFailedException e) {
            // Surface inner activity failure details in orchestration history/logs for easier Azure diagnostics.
            var details = e.getErrorDetails();
            var errorType = details == null ? "unknown" : details.getErrorType();
            var errorMessage = details == null ? e.getMessage() : details.getErrorMessage();
            throw new IllegalStateException(
                    "Collect orchestrator failed in activity " + activityName + " ("
                            + errorType + "): " + errorMessage,
                    e);
        }
    }

    /**
     * Activity that collects voting items for today.
     *
     * @param ignored          unused activity input
     * @param executionContext Azure Functions execution context
     * @return count of items upserted
     */
    @FunctionName(ACTIVITY_VOTINGS)
    public int collectVotings(
            @DurableActivityTrigger(name = "ignored") final String ignored,
            final ExecutionContext executionContext) {

        try {
            var date = LocalDate.now();
            var termNum = getCurrentTermNum();
            executionContext.getLogger().info(
                    "Starting votings collection for term=" + termNum + ", date=" + date);
            var count = collectService.collectVotings(termNum, date);
            executionContext.getLogger().info(
                    "Completed votings collection, count=" + count + ", term=" + termNum
                            + ", date=" + date);
            log.debug("Activity collectVotings completed: {} items", count);
            return count;
        } catch (Exception e) {
            log.error("Activity collectVotings failed", e);
            executionContext.getLogger().severe(
                "Activity collectVotings failed: " + buildFailureMessage(e));
            throw new IllegalStateException(
                    "Failed to collect votings: " + buildFailureMessage(e),
                    e);
        }
    }

    /**
     * Activity that collects committee sitting items for today.
     *
     * @param ignored          unused activity input
     * @param executionContext Azure Functions execution context
     * @return count of items upserted
     */
    @FunctionName(ACTIVITY_COMMITTEES)
    public int collectCommittees(
            @DurableActivityTrigger(name = "ignored") final String ignored,
            final ExecutionContext executionContext) {

        try {
            var date = LocalDate.now();
            var termNum = getCurrentTermNum();
            executionContext.getLogger().info(
                    "Starting committees collection for term=" + termNum + ", date=" + date);
            var count = collectService.collectCommitteeSittings(termNum, date);
            executionContext.getLogger().info(
                    "Completed committees collection, count=" + count + ", term=" + termNum
                            + ", date=" + date);
            log.debug("Activity collectCommittees completed: {} items", count);
            return count;
        } catch (Exception e) {
            log.error("Activity collectCommittees failed", e);
            executionContext.getLogger().severe(
                "Activity collectCommittees failed: " + buildFailureMessage(e));
            throw new IllegalStateException(
                    "Failed to collect committee sittings: " + buildFailureMessage(e),
                    e);
        }
    }

    /**
     * Activity that collects print items modified today.
     *
     * @param ignored          unused activity input
     * @param executionContext Azure Functions execution context
     * @return count of items upserted
     */
    @FunctionName(ACTIVITY_PRINTS)
    public int collectPrints(
            @DurableActivityTrigger(name = "ignored") final String ignored,
            final ExecutionContext executionContext) {

        try {
            var date = LocalDate.now();
            var termNum = getCurrentTermNum();
            executionContext.getLogger().info(
                    "Starting prints collection for term=" + termNum + ", date=" + date);
            var count = collectService.collectPrints(termNum, date);
            executionContext.getLogger().info(
                    "Completed prints collection, count=" + count + ", term=" + termNum
                            + ", date=" + date);
            log.debug("Activity collectPrints completed: {} items", count);
            return count;
        } catch (Exception e) {
            log.error("Activity collectPrints failed", e);
            executionContext.getLogger().severe(
                "Activity collectPrints failed: " + buildFailureMessage(e));
            throw new IllegalStateException(
                    "Failed to collect prints: " + buildFailureMessage(e),
                    e);
        }
    }

    /**
     * Activity that collects interpellation items modified today.
     *
     * @param ignored          unused activity input
     * @param executionContext Azure Functions execution context
     * @return count of items upserted
     */
    @FunctionName(ACTIVITY_INTERPELLATIONS)
    public int collectInterpellations(
            @DurableActivityTrigger(name = "ignored") final String ignored,
            final ExecutionContext executionContext) {

        try {
            var date = LocalDate.now();
            var termNum = getCurrentTermNum();
            executionContext.getLogger().info(
                    "Starting interpellations collection for term=" + termNum + ", date=" + date);
            var count = collectService.collectInterpellations(termNum, date);
            executionContext.getLogger().info(
                    "Completed interpellations collection, count=" + count + ", term=" + termNum
                            + ", date=" + date);
            log.debug("Activity collectInterpellations completed: {} items", count);
            return count;
        } catch (Exception e) {
            log.error("Activity collectInterpellations failed", e);
            executionContext.getLogger().severe(
                "Activity collectInterpellations failed: " + buildFailureMessage(e));
            throw new IllegalStateException(
                    "Failed to collect interpellations: " + buildFailureMessage(e),
                    e);
        }
    }

    /**
     * Activity that collects written question items modified today.
     *
     * @param ignored          unused activity input
     * @param executionContext Azure Functions execution context
     * @return count of items upserted
     */
    @FunctionName(ACTIVITY_QUESTIONS)
    public int collectQuestions(
            @DurableActivityTrigger(name = "ignored") final String ignored,
            final ExecutionContext executionContext) {

        try {
            var date = LocalDate.now();
            var termNum = getCurrentTermNum();
            executionContext.getLogger().info(
                    "Starting written questions collection for term=" + termNum + ", date=" + date);
            var count = collectService.collectWrittenQuestions(termNum, date);
            executionContext.getLogger().info(
                    "Completed written questions collection, count=" + count + ", term=" + termNum
                            + ", date=" + date);
            log.debug("Activity collectQuestions completed: {} items", count);
            return count;
        } catch (Exception e) {
            log.error("Activity collectQuestions failed", e);
            executionContext.getLogger().severe(
                "Activity collectQuestions failed: " + buildFailureMessage(e));
            throw new IllegalStateException(
                    "Failed to collect written questions: " + buildFailureMessage(e),
                    e);
        }
    }

    /**
     * Activity that collects bill items received today.
     *
     * @param ignored          unused activity input
     * @param executionContext Azure Functions execution context
     * @return count of items upserted
     */
    @FunctionName(ACTIVITY_BILLS)
    public int collectBills(
            @DurableActivityTrigger(name = "ignored") final String ignored,
            final ExecutionContext executionContext) {

        try {
            var date = LocalDate.now();
            var termNum = getCurrentTermNum();
            executionContext.getLogger().info(
                    "Starting bills collection for term=" + termNum + ", date=" + date);
            var count = collectService.collectBills(termNum, date);
            executionContext.getLogger().info(
                    "Completed bills collection, count=" + count + ", term=" + termNum + ", date="
                            + date);
            log.debug("Activity collectBills completed: {} items", count);
            return count;
        } catch (Exception e) {
            var failure = buildFailureMessage(e);
            // Bills are non-critical for the rest of the collection workflow.
            // Returning 0 keeps the orchestrator successful while preserving diagnostics.
            log.warn("Activity collectBills failed, continuing with partial result: {}", failure, e);
            executionContext.getLogger().warning(
                "Activity collectBills failed, continuing with count=0: " + failure);
            return 0;
        }
    }

    // Durable activity failures in Azure often surface only top-level exception messages.
    // Include cause type/message so Portal and instance history immediately show actionable details.
    private static String buildFailureMessage(final Exception exception) {
        var cause = exception.getCause();
        if (cause == null) {
            return exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        var causeMessage = cause.getMessage() == null ? "(no message)" : cause.getMessage();
        return cause.getClass().getSimpleName() + ": " + causeMessage;
    }

    /**
     * Returns the current Sejm term number, with caching to avoid repeated API calls.
     *
     * @return current term number
     * @throws IllegalStateException if no current term is found
     */
    private int getCurrentTermNum() {
        if (cachedTermNum instanceof CachedTerm.Resolved resolved) {
            return resolved.num();
        }
        var terms = sejmApiClient.fetchTerms();
        if (terms == null || terms.isEmpty()) {
            throw new IllegalStateException("No Sejm terms found");
        }
        var termNum = terms.stream()
                .filter(t -> t != null && t.current())
                .mapToInt(onlexnet.app.ports.out.SejmApiClient.SejmTerm::num)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No current Sejm term found among " + terms.size() + " terms"));
        cachedTermNum = new CachedTerm.Resolved(termNum);
        log.debug("Current Sejm term: {}", termNum);
        return termNum;
    }

    /**
     * Result record holding counts of collected items per data type.
     *
     * @param countsByType map of data type to count of upserted items
     */
    public record CollectResult(Map<String, Integer> countsByType) {
    }

    private record CollectOrchestrationInput(String coordinatorEntityId, String source) {
    }

    private record CollectCompletion(String orchestrationInstanceId) {
    }

    private record CollectFailure(String orchestrationInstanceId, String message) {
    }

    @SuppressWarnings("unused")
    public static final class CollectCoordinatorEntity extends AbstractTaskEntity<CollectCoordinatorState> {

        private static final CollectCoordinatorDecider DECIDER = new CollectCoordinatorDecider();

        @Override
        protected Class<CollectCoordinatorState> getStateType() {
            return CollectCoordinatorState.class;
        }

        @Override
        protected CollectCoordinatorState initializeState(final com.microsoft.durabletask.TaskEntityOperation operation) {
            return new CollectCoordinatorState();
        }

        public void requestCollect(final String source) {
            var decision = DECIDER.decide(
                    this.state.toDeciderState(),
                    new CollectCoordinatorDecider.RequestCollect(source));
            applyDecision(decision);
        }

        public void collectCompleted(final CollectCompletion completion) {
            var decision = DECIDER.decide(
                    this.state.toDeciderState(),
                    new CollectCoordinatorDecider.CollectCompleted(completion.orchestrationInstanceId()));
            applyDecision(decision);
        }

        public void collectFailed(final CollectFailure failure) {
            var decision = DECIDER.decide(
                    this.state.toDeciderState(),
                    new CollectCoordinatorDecider.CollectFailed(
                            failure.orchestrationInstanceId(),
                            failure.message()));
            applyDecision(decision);
        }

        private void applyDecision(final CollectCoordinatorDecider.Decision decision) {
            this.state.apply(decision.state());
            if (decision.effect() instanceof CollectCoordinatorDecider.Effect.StartCollectRun startCollectRun) {
                startNextRun(startCollectRun.source());
            }
        }

        private void startNextRun(final String source) {
            var options = new NewOrchestrationInstanceOptions();
            this.context.startNewOrchestration(
                    ORCHESTRATOR_FUNCTION_NAME,
                    new CollectOrchestrationInput(this.context.getId().toString(), source),
                    options);
        }
    }

    private static final class CollectCoordinatorState {
        private boolean running;
        private int pendingRequests;

        private CollectCoordinatorDecider.State toDeciderState() {
            return new CollectCoordinatorDecider.State(this.running, this.pendingRequests);
        }

        private void apply(final CollectCoordinatorDecider.State state) {
            this.running = state.running();
            this.pendingRequests = state.pendingRequests();
        }

    }
}
