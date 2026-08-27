package onlexnet.infra.adapters.in.azurefunc;

import static onlexnet.infra.adapters.in.azurefunc.CollectCoordinatorContractOperations.COLLECT_COMPLETED;
import static onlexnet.infra.adapters.in.azurefunc.CollectCoordinatorContractOperations.COLLECT_FAILED;
import static onlexnet.infra.adapters.in.azurefunc.CollectCoordinatorContractOperations.REQUEST_COLLECT;
import static onlexnet.infra.adapters.in.azurefunc.sejmTermSnapshot.SejmTermSnapshotContractOperations.TERM_SNAPSHOT_COLLECTED;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.RetryPolicy;
import com.microsoft.durabletask.Task;
import com.microsoft.durabletask.TaskFailedException;
import com.microsoft.durabletask.TaskOptions;
import com.microsoft.durabletask.TaskOrchestrationContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.app.ports.out.SejmDailyDigestPersistence;
import onlexnet.infra.adapters.in.azurefunc.sejmTermSnapshot.TermSnapshotCollectedEvent;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequest;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResult;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletion;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectFailure;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInput;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectResult;
import onlexnet.shared.Guards;

/**
 * Shared implementation for collect Azure Functions split across dedicated entrypoint classes.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SejmCollectFunctionSupport {

    private static final TaskOptions ACTIVITY_RETRY_OPTIONS = new TaskOptions(
            new RetryPolicy(3, Duration.ofSeconds(10))
                    .setBackoffCoefficient(2.0)
                    .setMaxRetryInterval(Duration.ofMinutes(2))
                    .setRetryTimeout(Duration.ofMinutes(10)));

    private static final EntityInstanceId COLLECT_COORDINATOR_ENTITY_ID =
            new EntityInstanceId(SejmCollectFunctions.COORDINATOR_ENTITY_NAME, SejmCollectFunctions.COORDINATOR_ENTITY_KEY);

    private final SejmCollectOperations collectService;
    private final SejmApiClient sejmApiClient;
    private final SejmDailyDigestPersistence dailyDigestPersistence;
    private final JsonValidator jsonValidator;
    private CachedTerm cachedTermNum = CachedTerm.NONE;

    private sealed interface CachedTerm permits CachedTerm.None, CachedTerm.Resolved {
        enum None implements CachedTerm {
            NONE
        }

        record Resolved(int num) implements CachedTerm {
        }

        CachedTerm NONE = None.NONE;
    }

    public void runTimer(
            String timerInfo,
            com.microsoft.durabletask.azurefunctions.DurableClientContext clientCtx,
            ExecutionContext execCtx) {

        try {
            var instanceId = enqueueCollectRequest(clientCtx, "timer");
            Log.info(execCtx, "Collect request accepted from timer, instanceId=" + instanceId);
            log.debug("Collect request from timer accepted, instanceId={}", instanceId);
        } catch (Exception e) {
            log.error("Failed to enqueue collect request", e);
            execCtx.getLogger().severe("Error enqueueing collect request: " + e.getMessage());
            throw new IllegalStateException("Failed to enqueue collection request", e);
        }
    }

    public HttpResponseMessage httpStart(
            HttpRequestMessage<Optional<String>> request,
            com.microsoft.durabletask.azurefunctions.DurableClientContext clientCtx,
            ExecutionContext execCtx) {

        try {
            var instanceId = enqueueCollectRequest(clientCtx, "http");
            Log.info(execCtx, "Manual collect request accepted, instanceId=" + instanceId);
            log.debug("Manual collect request accepted, instanceId={}", instanceId);
            return request.createResponseBuilder(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "accepted", true,
                            "coordinatorEntityId", instanceId,
                            "message", "Collect request was enqueued for serialized processing"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to enqueue collect request via HTTP", e);
            execCtx.getLogger().severe("Failed to enqueue collect request via HTTP: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to enqueue collection request: " + e.getMessage())
                    .build();
        }
    }

    public CollectResult runOrchestrator(TaskOrchestrationContext orchestrationContext) {
        var input = this.jsonValidator.validateReceivedIfPresent(
                JsonValidator.COLLECT_ORCHESTRATION_INPUT,
                orchestrationContext.getInput(CollectOrchestrationInput.class));
        var coordinatorEntityId = input == null
                ? COLLECT_COORDINATOR_ENTITY_ID
                : EntityInstanceId.fromString(input.getCoordinatorEntityId());
        var activitySource = input == null
                ? "orchestrator"
                : Guards.orDefaultIfNullOrEmpty(input.getSource(), "orchestrator");

        var votingTask = startActivityWithRetry(orchestrationContext, SejmCollectFunctions.ACTIVITY_VOTINGS, activitySource);
        var committeesTask = startActivityWithRetry(orchestrationContext, SejmCollectFunctions.ACTIVITY_COMMITTEES, activitySource);
        var printsTask = startActivityWithRetry(orchestrationContext, SejmCollectFunctions.ACTIVITY_PRINTS, activitySource);
        var interpellationsTask = startActivityWithRetry(
            orchestrationContext,
            SejmCollectFunctions.ACTIVITY_INTERPELLATIONS,
            activitySource);
        var questionsTask = startActivityWithRetry(orchestrationContext, SejmCollectFunctions.ACTIVITY_QUESTIONS, activitySource);
        var billsTask = startActivityWithRetry(orchestrationContext, SejmCollectFunctions.ACTIVITY_BILLS, activitySource);

        try {
            orchestrationContext.allOf(List.of(
                    votingTask,
                    committeesTask,
                    printsTask,
                    interpellationsTask,
                    questionsTask,
                    billsTask)).await();
        } catch (TaskFailedException e) {
            var wrapped = new IllegalStateException(
                    "Collect orchestrator failed while waiting for activity completion: " + taskFailureSummary(e),
                    e);
            signalCollectFailed(orchestrationContext, coordinatorEntityId, wrapped);
            throw wrapped;
        }

        try {
            var votingResult = awaitActivityWithFailureContext(votingTask, SejmCollectFunctions.ACTIVITY_VOTINGS);
            var committeesResult = awaitActivityWithFailureContext(committeesTask, SejmCollectFunctions.ACTIVITY_COMMITTEES);
            var printsResult = awaitActivityWithFailureContext(printsTask, SejmCollectFunctions.ACTIVITY_PRINTS);
            var interpellationsResult = awaitActivityWithFailureContext(interpellationsTask, SejmCollectFunctions.ACTIVITY_INTERPELLATIONS);
            var questionsResult = awaitActivityWithFailureContext(questionsTask, SejmCollectFunctions.ACTIVITY_QUESTIONS);
            var billsResult = awaitActivityWithFailureContext(billsTask, SejmCollectFunctions.ACTIVITY_BILLS);

            var counts = new HashMap<String, Integer>();
            counts.put("VOTING", requireCount(votingResult));
            counts.put("COMMITTEE_SITTING", requireCount(committeesResult));
            counts.put("PRINT", requireCount(printsResult));
            counts.put("INTERPELLATION", requireCount(interpellationsResult));
            counts.put("WRITTEN_QUESTION", requireCount(questionsResult));
            counts.put("BILL", requireCount(billsResult));

            reconcileTermSnapshot(orchestrationContext, activitySource, interpellationsResult, questionsResult, printsResult, billsResult);

            var result = new CollectResult();
            result.setCountsByType(Collections.unmodifiableMap(new HashMap<>(counts)));
            this.jsonValidator.validateToSend(JsonValidator.COLLECT_RESULT, result);
            var completion = new CollectCompletion();
            completion.setOrchestrationInstanceId(orchestrationContext.getInstanceId());
            this.jsonValidator.validateToSend(JsonValidator.COLLECT_COMPLETION, completion);
            orchestrationContext.signalEntity(coordinatorEntityId, COLLECT_COMPLETED.methodName(), completion);
            return result;
        } catch (RuntimeException e) {
            signalCollectFailed(orchestrationContext, coordinatorEntityId, e);
            throw e;
        }
    }

    public CollectActivityResult collectVotings(CollectActivityRequest request, ExecutionContext execCtx) {
        validateActivityRequest(request);

        try {
            var date = LocalDate.now();
            var termNum = getCurrentTermNum();
            Log.info(execCtx, "Starting votings collection for term=" + termNum + ", date=" + date);
            var count = collectService.collectVotings(termNum, date);
            Log.info(execCtx, "Completed votings collection, count=" + count + ", term=" + termNum + ", date=" + date);
            log.debug("Activity collectVotings completed: {} items", count);
            return buildActivityResult(count, termNum, date, List.of(), Map.of());
        } catch (Exception e) {
            log.error("Activity collectVotings failed", e);
            execCtx.getLogger().severe("Activity collectVotings failed: " + buildFailureMessage(e));
            throw new IllegalStateException("Failed to collect votings: " + buildFailureMessage(e), e);
        }
    }

    public CollectActivityResult collectCommittees(CollectActivityRequest request, ExecutionContext execCtx) {
        validateActivityRequest(request);

        try {
            var date = LocalDate.now();
            var termNum = getCurrentTermNum();
            Log.info(execCtx, "Starting committees collection for term=" + termNum + ", date=" + date);
            var count = collectService.collectCommitteeSittings(termNum, date);
            Log.info(execCtx, "Completed committees collection, count=" + count + ", term=" + termNum + ", date=" + date);
            log.debug("Activity collectCommittees completed: {} items", count);
            return buildActivityResult(count, termNum, date, List.of(), Map.of());
        } catch (Exception e) {
            log.error("Activity collectCommittees failed", e);
            execCtx.getLogger().severe("Activity collectCommittees failed: " + buildFailureMessage(e));
            throw new IllegalStateException("Failed to collect committee sittings: " + buildFailureMessage(e), e);
        }
    }

    public CollectActivityResult collectPrints(CollectActivityRequest request, ExecutionContext execCtx) {
        validateActivityRequest(request);

        try {
            var date = LocalDate.now();
            var termNum = getCurrentTermNum();
            Log.info(execCtx, "Starting prints collection for term=" + termNum + ", date=" + date);
            var count = collectService.collectPrints(termNum, date);
            Log.info(execCtx, "Completed prints collection, count=" + count + ", term=" + termNum + ", date=" + date);
            log.debug("Activity collectPrints completed: {} items", count);
            return buildActivityResult(count, termNum, date, loadKeysByType(date, "PRINT"), Map.of());
        } catch (Exception e) {
            log.error("Activity collectPrints failed", e);
            execCtx.getLogger().severe("Activity collectPrints failed: " + buildFailureMessage(e));
            throw new IllegalStateException("Failed to collect prints: " + buildFailureMessage(e), e);
        }
    }

    public CollectActivityResult collectInterpellations(CollectActivityRequest request, ExecutionContext execCtx) {
        validateActivityRequest(request);

        try {
            var date = LocalDate.now();
            var termNum = getCurrentTermNum();
            Log.info(execCtx, "Starting interpellations collection for term=" + termNum + ", date=" + date);
            var count = collectService.collectInterpellations(termNum, date);
            Log.info(execCtx, "Completed interpellations collection, count=" + count + ", term=" + termNum + ", date=" + date);
            log.debug("Activity collectInterpellations completed: {} items", count);
                var interpellationFingerprints = loadInterpellationFingerprints(date);
                return buildActivityResult(
                    count,
                    termNum,
                    date,
                        List.copyOf(new TreeSet<>(interpellationFingerprints.keySet())),
                    interpellationFingerprints);
        } catch (Exception e) {
            log.error("Activity collectInterpellations failed", e);
            execCtx.getLogger().severe("Activity collectInterpellations failed: " + buildFailureMessage(e));
            throw new IllegalStateException("Failed to collect interpellations: " + buildFailureMessage(e), e);
        }
    }

    public CollectActivityResult collectQuestions(CollectActivityRequest request, ExecutionContext execCtx) {
        validateActivityRequest(request);

        try {
            var date = LocalDate.now();
            var termNum = getCurrentTermNum();
            Log.info(execCtx, "Starting written questions collection for term=" + termNum + ", date=" + date);
            var count = collectService.collectWrittenQuestions(termNum, date);
            Log.info(execCtx, "Completed written questions collection, count=" + count + ", term=" + termNum + ", date=" + date);
            log.debug("Activity collectQuestions completed: {} items", count);
            return buildActivityResult(count, termNum, date, loadKeysByType(date, "WRITTEN_QUESTION"), Map.of());
        } catch (Exception e) {
            log.error("Activity collectQuestions failed", e);
            execCtx.getLogger().severe("Activity collectQuestions failed: " + buildFailureMessage(e));
            throw new IllegalStateException("Failed to collect written questions: " + buildFailureMessage(e), e);
        }
    }

    public CollectActivityResult collectBills(CollectActivityRequest request, ExecutionContext execCtx) {
        validateActivityRequest(request);

        try {
            var date = LocalDate.now();
            var termNum = getCurrentTermNum();
            Log.info(execCtx, "Starting bills collection for term=" + termNum + ", date=" + date);
            var count = collectService.collectBills(termNum, date);
            Log.info(execCtx, "Completed bills collection, count=" + count + ", term=" + termNum + ", date=" + date);
            log.debug("Activity collectBills completed: {} items", count);
            return buildActivityResult(count, termNum, date, loadKeysByType(date, "BILL"), Map.of());
        } catch (Exception e) {
            var failure = buildFailureMessage(e);
            log.warn("Activity collectBills failed, continuing with partial result: {}", failure, e);
            execCtx.getLogger().warning("Activity collectBills failed, continuing with count=0: " + failure);
            return buildActivityResult(0, getCurrentTermNum(), LocalDate.now(), List.of(), Map.of());
        }
    }

    private String enqueueCollectRequest(
            com.microsoft.durabletask.azurefunctions.DurableClientContext clientCtx,
            String source) {
        var client = clientCtx.getClient().getEntities();
        client.signalEntity(COLLECT_COORDINATOR_ENTITY_ID, REQUEST_COLLECT.methodName(), source);
        return COLLECT_COORDINATOR_ENTITY_ID.toString();
    }

    private Task<CollectActivityResult> startActivityWithRetry(
            TaskOrchestrationContext orchestrationContext,
            String activityName,
            String source) {
        var request = new CollectActivityRequest();
        request.setSource(source);
        this.jsonValidator.validateToSend(JsonValidator.COLLECT_ACTIVITY_REQUEST, request);
        return orchestrationContext.callActivity(
                activityName,
                request,
                ACTIVITY_RETRY_OPTIONS,
                CollectActivityResult.class);
    }

    private CollectActivityResult awaitActivityWithFailureContext(Task<CollectActivityResult> task, String activityName) {
        try {
            return this.jsonValidator.validateReceived(JsonValidator.COLLECT_ACTIVITY_RESULT, task.await());
        } catch (TaskFailedException e) {
            var details = e.getErrorDetails();
            var errorType = details == null ? "unknown" : details.getErrorType();
            var errorMessage = details == null ? e.getMessage() : details.getErrorMessage();
            throw new IllegalStateException(
                    "Collect orchestrator failed in activity " + activityName + " (" + errorType + "): " + errorMessage,
                    e);
        }
    }

        private void reconcileTermSnapshot(
            TaskOrchestrationContext orchestrationContext,
            String activitySource,
            CollectActivityResult interpellationsResult,
            CollectActivityResult questionsResult,
            CollectActivityResult printsResult,
            CollectActivityResult billsResult) {
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

    private static int requireCount(CollectActivityResult result) {
        return Objects.requireNonNull(result.getCount(), "Activity result count must not be null");
    }

    private static int requireSnapshotTermNum(CollectActivityResult result) {
        return Objects.requireNonNull(result.getTermNum(), "Activity result termNum must not be null");
    }

    private static LocalDate requireSnapshotDate(CollectActivityResult result) {
        return Objects.requireNonNull(result.getCollectionDate(), "Activity result collectionDate must not be null");
    }

    private static List<String> orEmptyList(@Nullable List<String> value) {
        return value == null ? List.of() : value;
    }

    private static Map<String, String> orEmptyMap(@Nullable Map<String, String> value) {
        return value == null ? Map.of() : value;
    }

    private Map<String, String> loadInterpellationFingerprints(LocalDate date) {
        var rows = this.dailyDigestPersistence.findByDateAndType(date, "INTERPELLATION");
        var byKey = new TreeMap<String, String>();
        for (var row : rows) {
            var key = extractStringColumn(row, "item_key");
            var json = extractJsonColumn(row, "item_json");
            byKey.put(key, sha256Hex(json));
        }
        return Map.copyOf(byKey);
    }

    private List<String> loadKeysByType(LocalDate date, String dataType) {
        var rows = this.dailyDigestPersistence.findByDateAndType(date, dataType);
        var keys = new TreeSet<String>();
        for (var row : rows) {
            keys.add(extractStringColumn(row, "item_key"));
        }
        return List.copyOf(keys);
    }

    private static String extractStringColumn(Map<String, Object> row, String key) {
        var value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        if (value == null) {
            throw new IllegalStateException("Missing required column '" + key + "' in digest row");
        }
        return String.valueOf(value);
    }

    private static String extractJsonColumn(Map<String, Object> row, String key) {
        var value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        if (value == null) {
            throw new IllegalStateException("Missing required column '" + key + "' in digest row");
        }
        if (value instanceof CharSequence sequence) {
            return sequence.toString();
        }
        if (!"org.postgresql.util.PGobject".equals(value.getClass().getName())) {
            return String.valueOf(value);
        }
        try {
            var getValueMethod = value.getClass().getMethod("getValue");
            var extracted = getValueMethod.invoke(value);
            return extracted == null ? "" : String.valueOf(extracted);
        } catch (ReflectiveOperationException exception) {
            return String.valueOf(value);
        }
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void validateActivityRequest(CollectActivityRequest request) {
        var normalizedRequest = request == null ? new CollectActivityRequest() : request;
        this.jsonValidator.validateReceived(JsonValidator.COLLECT_ACTIVITY_REQUEST, normalizedRequest);
    }

    private CollectActivityResult buildActivityResult(
            int count,
            int termNum,
            LocalDate date,
            List<String> itemKeys,
            Map<String, String> interpellationFingerprints) {
        var result = new CollectActivityResult();
        result.setCount(count);
        result.setTermNum(termNum);
        result.setCollectionDate(date);
        result.setItemKeys(List.copyOf(itemKeys));
        result.setInterpellationFingerprints(Map.copyOf(interpellationFingerprints));
        return this.jsonValidator.validateToSend(JsonValidator.COLLECT_ACTIVITY_RESULT, result);
    }

    private static String buildFailureMessage(Exception exception) {
        var cause = exception.getCause();
        if (cause == null) {
            return exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        var causeMessage = cause.getMessage() == null ? "(no message)" : cause.getMessage();
        return cause.getClass().getSimpleName() + ": " + causeMessage;
    }

    private static String orchestrationFailureMessage(RuntimeException exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private static String taskFailureSummary(TaskFailedException exception) {
        var details = exception.getErrorDetails();
        if (details == null) {
            return orchestrationFailureMessage(exception);
        }
        var errorType = details.getErrorType() == null ? "unknown" : details.getErrorType();
        var errorMessage = details.getErrorMessage() == null ? "(no message)" : details.getErrorMessage();
        return errorType + ": " + errorMessage;
    }

    private void signalCollectFailed(
            TaskOrchestrationContext orchestrationContext,
            EntityInstanceId coordinatorEntityId,
            RuntimeException exception) {
        var failure = new CollectFailure();
        failure.setOrchestrationInstanceId(orchestrationContext.getInstanceId());
        failure.setMessage(orchestrationFailureMessage(exception));
        this.jsonValidator.validateToSend(JsonValidator.COLLECT_FAILURE, failure);
        orchestrationContext.signalEntity(coordinatorEntityId, COLLECT_FAILED.methodName(), failure);
    }

    private int getCurrentTermNum() {
        if (cachedTermNum instanceof CachedTerm.Resolved resolved) {
            return resolved.num();
        }
        var terms = Guards.requireNonEmpty(
                sejmApiClient.fetchTerms(),
                () -> new IllegalStateException("No Sejm terms found"));
        var termNum = terms.stream()
                .filter(t -> t != null && t.current())
                .mapToInt(onlexnet.app.ports.out.SejmApiClient.SejmTerm::num)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No current Sejm term found among " + terms.size() + " terms"));
        cachedTermNum = new CachedTerm.Resolved(termNum);
        log.debug("Current Sejm term: {}", termNum);
        return termNum;
    }
}
