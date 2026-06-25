package onlexnet.sejmapi;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.durabletask.RetryPolicy;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.TaskOptions;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onlexnet.app.ports.out.SejmApiClient;

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
    static final String TIMER_FUNCTION_NAME = "Fun_SejmCollectTimer";
    /** HTTP starter function name for manual trigger. */
    static final String HTTP_STARTER_FUNCTION_NAME = "Fun_CollectStart";
    /** Durable orchestrator function name. */
    static final String ORCHESTRATOR_FUNCTION_NAME = "Fun_CollectOrchestrator";
    /** Activity function name for collecting votings. */
    static final String ACTIVITY_VOTINGS = "Intern_CollectVotings";
    /** Activity function name for collecting committee sittings. */
    static final String ACTIVITY_COMMITTEES = "Intern_CollectCommittees";
    /** Activity function name for collecting prints. */
    static final String ACTIVITY_PRINTS = "Intern_CollectPrints";
    /** Activity function name for collecting interpellations. */
    static final String ACTIVITY_INTERPELLATIONS = "Intern_CollectInterpellations";
    /** Activity function name for collecting written questions. */
    static final String ACTIVITY_QUESTIONS = "Intern_CollectQuestions";
    /** Activity function name for collecting bills. */
    static final String ACTIVITY_BILLS = "Intern_CollectBills";
    private static final TaskOptions ORCHESTRATOR_ACTIVITY_OPTIONS = new TaskOptions(
            new RetryPolicy(3, Duration.ofSeconds(5))
                    .setBackoffCoefficient(2.0)
                    .setMaxRetryInterval(Duration.ofMinutes(1))
                    .setRetryTimeout(Duration.ofMinutes(5)));

    private final SejmCollectService collectService;
    private final SejmApiClient sejmApiClient;
    private CachedTerm cachedTermNum = CachedTerm.NONE;

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
            var instanceId = durableContext.getClient()
                .scheduleNewOrchestrationInstance(ORCHESTRATOR_FUNCTION_NAME, (Object) null);
            executionContext.getLogger().info(
                    "Successfully scheduled collect orchestration, instanceId=" + instanceId);
            log.debug("Collect orchestrator triggered by timer: {}", instanceId);
        } catch (Exception e) {
            log.error("Failed to schedule collect orchestration", e);
            executionContext.getLogger().severe("Error scheduling orchestration: " + e.getMessage());
            throw new IllegalStateException("Failed to start collection orchestration", e);
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
            var instanceId = durableContext.getClient()
                    .scheduleNewOrchestrationInstance(ORCHESTRATOR_FUNCTION_NAME, (Object) null);
            executionContext.getLogger().info(
                "Manually scheduled collect orchestration, instanceId=" + instanceId);
            log.debug("Manual collect orchestrator triggered: {}", instanceId);
            return durableContext.createCheckStatusResponse(request, instanceId);
        } catch (Exception e) {
            log.error("Failed to schedule collect orchestration via HTTP", e);
            executionContext.getLogger().severe(
                "Failed to schedule collect orchestration via HTTP: " + e.getMessage());
            return request.createResponseBuilder(com.microsoft.azure.functions.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to start collection: " + e.getMessage())
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

        try {
            var counts = new HashMap<String, Integer>();

            counts.put("VOTING", callCountActivity(orchestrationContext, ACTIVITY_VOTINGS));
            counts.put("COMMITTEE_SITTING", callCountActivity(orchestrationContext, ACTIVITY_COMMITTEES));
            counts.put("PRINT", callCountActivity(orchestrationContext, ACTIVITY_PRINTS));
            counts.put("INTERPELLATION", callCountActivity(orchestrationContext, ACTIVITY_INTERPELLATIONS));
            counts.put("WRITTEN_QUESTION", callCountActivity(orchestrationContext, ACTIVITY_QUESTIONS));
            counts.put("BILL", callCountActivity(orchestrationContext, ACTIVITY_BILLS));

            return new CollectResult(Map.copyOf(counts));
        } catch (Exception e) {
            log.error("Orchestrator failed", e);
            throw new IllegalStateException("Collection orchestration failed", e);
        }
    }

    private Integer callCountActivity(
            final TaskOrchestrationContext orchestrationContext,
            final String activityName) {
        return Objects.requireNonNull(
                orchestrationContext
                        .callActivity(activityName, null, ORCHESTRATOR_ACTIVITY_OPTIONS, Integer.class)
                        .await(),
                "Activity " + activityName + " returned null count");
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
                    "Activity collectVotings failed: " + e.getMessage());
            throw new IllegalStateException("Failed to collect votings", e);
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
                    "Activity collectCommittees failed: " + e.getMessage());
            throw new IllegalStateException("Failed to collect committee sittings", e);
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
                    "Activity collectPrints failed: " + e.getMessage());
            throw new IllegalStateException("Failed to collect prints", e);
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
                    "Activity collectInterpellations failed: " + e.getMessage());
            throw new IllegalStateException("Failed to collect interpellations", e);
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
                    "Activity collectQuestions failed: " + e.getMessage());
            throw new IllegalStateException("Failed to collect written questions", e);
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
            log.error("Activity collectBills failed", e);
            executionContext.getLogger().severe(
                    "Activity collectBills failed: " + e.getMessage());
            throw new IllegalStateException("Failed to collect bills", e);
        }
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
}
