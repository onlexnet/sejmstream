package onlexnet.sejmapi;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;

import onlexnet.app.ports.out.SejmApiClient;

/**
 * Azure Durable Functions workflow that collects daily Sejm activity into the database.
 * Implements a 6-activity orchestrator pattern that sequentially collects votings, committee
 * sittings, prints, interpellations, written questions, and bills. Triggered via timer every hour
 * or manually via HTTP POST. Results are persisted in the daily digest tables.
 */
@Component
public final class SejmCollectFunctions {

    private static final Logger LOGGER = Logger.getLogger(SejmCollectFunctions.class.getName());

    /** Timer trigger function name. */
    static final String TIMER_FUNCTION_NAME = "Fun_CollectTimer";
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

    private final SejmCollectService collectService;
    private final SejmApiClient sejmApiClient;
    private int cachedTermNum = -1;

    public SejmCollectFunctions(final SejmCollectService collectService,
            final SejmApiClient sejmApiClient) {
        this.collectService = Objects.requireNonNull(collectService, "collectService must not be null");
        this.sejmApiClient = Objects.requireNonNull(sejmApiClient, "sejmApiClient must not be null");
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
                    .scheduleNewOrchestrationInstance(ORCHESTRATOR_FUNCTION_NAME, null);
            executionContext.getLogger().info(
                    "Successfully scheduled collect orchestration, instanceId=" + instanceId);
            LOGGER.fine("Collect orchestrator triggered by timer: " + instanceId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to schedule collect orchestration", e);
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
            final DurableClientContext durableContext) {

        try {
            var instanceId = durableContext.getClient()
                    .scheduleNewOrchestrationInstance(ORCHESTRATOR_FUNCTION_NAME, null);
            LOGGER.fine("Manual collect orchestrator triggered: " + instanceId);
            return durableContext.createCheckStatusResponse(request, instanceId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to schedule collect orchestration via HTTP", e);
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

            counts.put("VOTING", orchestrationContext
                    .callActivity(ACTIVITY_VOTINGS, null, Integer.class).await());
            counts.put("COMMITTEE_SITTING", orchestrationContext
                    .callActivity(ACTIVITY_COMMITTEES, null, Integer.class).await());
            counts.put("PRINT", orchestrationContext
                    .callActivity(ACTIVITY_PRINTS, null, Integer.class).await());
            counts.put("INTERPELLATION", orchestrationContext
                    .callActivity(ACTIVITY_INTERPELLATIONS, null, Integer.class).await());
            counts.put("WRITTEN_QUESTION", orchestrationContext
                    .callActivity(ACTIVITY_QUESTIONS, null, Integer.class).await());
            counts.put("BILL", orchestrationContext
                    .callActivity(ACTIVITY_BILLS, null, Integer.class).await());

            return new CollectResult(Map.copyOf(counts));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Orchestrator failed", e);
            throw new IllegalStateException("Collection orchestration failed", e);
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
            var count = collectService.collectVotings(termNum, date);
            LOGGER.fine("Activity collectVotings completed: " + count + " items");
            return count;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Activity collectVotings failed", e);
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
            var count = collectService.collectCommitteeSittings(termNum, date);
            LOGGER.fine("Activity collectCommittees completed: " + count + " items");
            return count;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Activity collectCommittees failed", e);
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
            var count = collectService.collectPrints(termNum, date);
            LOGGER.fine("Activity collectPrints completed: " + count + " items");
            return count;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Activity collectPrints failed", e);
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
            var count = collectService.collectInterpellations(termNum, date);
            LOGGER.fine("Activity collectInterpellations completed: " + count + " items");
            return count;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Activity collectInterpellations failed", e);
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
            var count = collectService.collectWrittenQuestions(termNum, date);
            LOGGER.fine("Activity collectQuestions completed: " + count + " items");
            return count;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Activity collectQuestions failed", e);
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
            var count = collectService.collectBills(termNum, date);
            LOGGER.fine("Activity collectBills completed: " + count + " items");
            return count;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Activity collectBills failed", e);
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
        if (cachedTermNum > 0) {
            return cachedTermNum;
        }
        var terms = sejmApiClient.fetchTerms();
        if (terms == null || terms.isEmpty()) {
            throw new IllegalStateException("No Sejm terms found");
        }
        cachedTermNum = terms.stream()
                .filter(t -> t != null && t.current())
                .mapToInt(onlexnet.app.ports.out.SejmApiClient.SejmTerm::num)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No current Sejm term found among " + terms.size() + " terms"));
        LOGGER.fine("Current Sejm term: " + cachedTermNum);
        return cachedTermNum;
    }

    /**
     * Result record holding counts of collected items per data type.
     *
     * @param countsByType map of data type to count of upserted items
     */
    public record CollectResult(Map<String, Integer> countsByType) {
    }
}
