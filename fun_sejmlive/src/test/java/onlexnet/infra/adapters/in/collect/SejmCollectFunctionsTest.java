package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.durabletask.CleanEntityStorageRequest;
import com.microsoft.durabletask.CleanEntityStorageResult;
import com.microsoft.durabletask.DurableTaskClient;
import com.microsoft.durabletask.DurableEntityClient;
import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.EntityMetadata;
import com.microsoft.durabletask.EntityQuery;
import com.microsoft.durabletask.EntityQueryResult;
import com.microsoft.durabletask.NewOrchestrationInstanceOptions;
import com.microsoft.durabletask.OrchestrationMetadata;
import com.microsoft.durabletask.OrchestrationStatusQuery;
import com.microsoft.durabletask.OrchestrationStatusQueryResult;
import com.microsoft.durabletask.PurgeInstanceCriteria;
import com.microsoft.durabletask.PurgeResult;
import com.microsoft.durabletask.Task;
import com.microsoft.durabletask.TaskOptions;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableEntityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmApiClient.SejmPrints;
import onlexnet.app.ports.out.SejmApiClient.SejmTerm;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.infra.adapters.in.azurefunc.CollectCoordinatorEntity;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectCoordinatorEntityFunctions;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequest;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResult;
import onlexnet.infra.adapters.out.SejmCollectService;
import onlexnet.testsupport.AppTest;

@AppTest
class SejmCollectFunctionsTest {

    private static final String COORDINATOR_ENTITY_NAME = "CollectCoordinator";
    private static final String COORDINATOR_ENTITY_KEY = "singleton";
    private static final List<String> COLLECT_COORDINATOR_OPERATIONS = List.of("requestCollect", "collectCompleted",
            "collectFailed");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SejmCollectFunctions sejmCollectFunctions;

    @Autowired
    private SejmCollectService sejmCollectService;

    @Autowired
    private SejmApiClient sejmApiClient;

    @Test
    void givenSpringBootContext_whenResolvingCollectFunctions_thenRequiredDependenciesAreInjected() {
        assertThat(this.applicationContext).isNotNull();
        assertThat(this.sejmCollectFunctions).isNotNull();
        assertThat(this.sejmCollectService).isNotNull();
        assertThat(this.sejmApiClient).isNotNull();
        assertThat(this.applicationContext.getBean(SejmCollectFunctions.class)).isSameAs(this.sejmCollectFunctions);
        assertThat(this.applicationContext.getBean(SejmCollectService.class)).isSameAs(this.sejmCollectService);
        assertThat(this.applicationContext.getBean(SejmApiClient.class)).isSameAs(this.sejmApiClient);
    }

    @Test
    void givenTimerFunction_whenCheckingTriggerContract_thenRunsDailyAt1630() throws NoSuchMethodException {
        var method = SejmCollectFunctions.class.getDeclaredMethod(
            "runTimer",
            String.class,
            DurableClientContext.class,
            ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(TimerTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo(SejmCollectFunctions.TIMER_FUNCTION_NAME);
        assertThat(trigger).isNotNull();
        assertThat(trigger.schedule()).isEqualTo("0 0 * * * *");
    }

    @Test
    void givenHttpStarterFunction_whenCheckingTriggerContract_thenPostAndFunctionAuthAreConfigured()
            throws NoSuchMethodException {
        var method = SejmCollectFunctions.class.getDeclaredMethod(
                "httpStart",
                HttpRequestMessage.class,
                DurableClientContext.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(HttpTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value())
                .isEqualTo(SejmCollectFunctions.HTTP_STARTER_FUNCTION_NAME);
        assertThat(trigger).isNotNull();
        assertThat(trigger.methods()).containsExactly(HttpMethod.POST);
        assertThat(trigger.authLevel()).isEqualTo(AuthorizationLevel.FUNCTION);
    }

    @Test
    void givenOrchestratorFunction_whenCheckingTriggerContract_thenFunctionAndOrchestrationTriggerAreConfigured()
            throws NoSuchMethodException {
        var method = SejmCollectFunctions.class.getDeclaredMethod(
                "runOrchestrator",
                com.microsoft.durabletask.TaskOrchestrationContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0]
                .getAnnotation(DurableOrchestrationTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value())
                .isEqualTo(SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME);
        assertThat(trigger).isNotNull();
        assertThat(trigger.name()).isEqualTo("orchestrationContext");
    }

    @Test
    void givenCoordinatorEntityFunction_whenCheckingTriggerContract_thenFunctionAndEntityTriggerAreConfigured()
            throws NoSuchMethodException {
        var method = SejmCollectCoordinatorEntityFunctions.class.getDeclaredMethod(
                "runCollectCoordinatorEntity",
                String.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(DurableEntityTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo(SejmCollectFunctions.COORDINATOR_ENTITY_FUNCTION_NAME);
        assertThat(trigger).isNotNull();
        assertThat(trigger.name()).isEqualTo("entityRequest");
        assertThat(trigger.entityName()).isEqualTo(COORDINATOR_ENTITY_NAME);
    }

    @Test
    void givenActivities_whenCheckingTriggerContract_thenFunctionAndActivityTriggersAreConfigured()
            throws NoSuchMethodException {
        assertActivityContract("collectVotings", SejmCollectFunctions.ACTIVITY_VOTINGS);
        assertActivityContract("collectCommittees", SejmCollectFunctions.ACTIVITY_COMMITTEES);
        assertActivityContract("collectPrints", SejmCollectFunctions.ACTIVITY_PRINTS);
        assertActivityContract("collectInterpellations",
                SejmCollectFunctions.ACTIVITY_INTERPELLATIONS);
        assertActivityContract("collectQuestions", SejmCollectFunctions.ACTIVITY_QUESTIONS);
        assertActivityContract("collectBills", SejmCollectFunctions.ACTIVITY_BILLS);
    }

    @Test
    void givenTimerTrigger_whenInvoked_thenSignalsCollectCoordinatorEntity() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var functions = newFunctions(collectService, sejmApiClient);
        var clientCtx = new TestDurableClientContext(false);

        functions.runTimer("timer", clientCtx, new FakeExecutionContext());

        assertThat(clientCtx.lastSignaledEntityId)
                .isEqualTo(new EntityInstanceId(
                        COORDINATOR_ENTITY_NAME,
                        COORDINATOR_ENTITY_KEY));
        assertThat(clientCtx.lastEntityOperationName)
                .isEqualTo("requestCollect");
        assertThat(clientCtx.lastEntityPayload).isEqualTo("timer");
    }

    @Test
    void givenTimerTrigger_whenSchedulingFails_thenThrowsIllegalStateException() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var functions = newFunctions(collectService, sejmApiClient);
        var clientCtx = new TestDurableClientContext(true);

        assertThatThrownBy(
                () -> functions.runTimer("timer", clientCtx, new FakeExecutionContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to enqueue collection request")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void givenHttpStart_whenInvoked_thenReturnsAcceptedQueuedResponse() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var functions = newFunctions(collectService, sejmApiClient);
        var clientCtx = new TestDurableClientContext(false);
        var request = new FakeHttpRequestMessage<String>(Optional.empty());

        var response = functions.httpStart(request, clientCtx, new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeader("Location")).isNull();
        assertThat(response.getBody()).isEqualTo(Map.of(
                "accepted", true,
                "coordinatorEntityId", new EntityInstanceId(
                        COORDINATOR_ENTITY_NAME,
                        COORDINATOR_ENTITY_KEY).toString(),
                "message", "Collect request was enqueued for serialized processing"));
    }

    @Test
    void givenHttpStart_whenSchedulingFails_thenReturnsInternalServerError() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var functions = newFunctions(collectService, sejmApiClient);
        var clientCtx = new TestDurableClientContext(true);
        var request = new FakeHttpRequestMessage<String>(Optional.empty());

        var response = functions.httpStart(request, clientCtx, new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().toString()).contains("Failed to enqueue collection request");
    }

    @Test
    void givenMultipleTimerTriggers_whenInvoked_thenEachSignalsCoordinatorEntity() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var functions = newFunctions(collectService, sejmApiClient);
        var clientCtx = new TestDurableClientContext(false);

        functions.runTimer("timer-1", clientCtx, new FakeExecutionContext());
        clientCtx.resetCapturedSignals();
        functions.runTimer("timer-2", clientCtx, new FakeExecutionContext());

        assertThat(clientCtx.lastSignaledEntityId)
                .isEqualTo(new EntityInstanceId(
                        COORDINATOR_ENTITY_NAME,
                        COORDINATOR_ENTITY_KEY));
        assertThat(clientCtx.lastEntityOperationName).isEqualTo("requestCollect");
        assertThat(clientCtx.lastEntityPayload).isEqualTo("timer");
    }

    @Test
    void givenActivities_whenInvoked_thenDelegateToServiceUsingCurrentTermAndToday() {
        var collectService = mock(SejmCollectOperations.class);
        when(collectService.collectVotings(eq(10), any(LocalDate.class))).thenReturn(11);
        when(collectService.collectBills(eq(10), any(LocalDate.class))).thenReturn(22);
        var sejmApiClient = mock(SejmApiClient.class);
        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(false, LocalDate.of(2023, 1, 1), 9,
                        new SejmPrints(0, null, "/term9/prints"),
                        LocalDate.of(2023, 10, 10)),
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                        new SejmPrints(10, null, "/term10/prints"),
                        null)));
        var functions = newFunctions(collectService, sejmApiClient);

        var beforeVotings = LocalDate.now();
        var votingResult = functions.collectVotings(null, new FakeExecutionContext());
        var afterVotings = LocalDate.now();

        var beforeBills = LocalDate.now();
        var billsResult = functions.collectBills(null, new FakeExecutionContext());
        var afterBills = LocalDate.now();

        assertThat(votingResult.getCount()).isEqualTo(11);
        assertThat(billsResult.getCount()).isEqualTo(22);
        var votingsDateCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        var billsDateCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        verify(collectService, times(1)).collectVotings(eq(10), votingsDateCaptor.capture());
        verify(collectService, times(1)).collectBills(eq(10), billsDateCaptor.capture());
        assertThat(votingsDateCaptor.getValue()).isBetween(beforeVotings, afterVotings);
        assertThat(billsDateCaptor.getValue()).isBetween(beforeBills, afterBills);
        verify(sejmApiClient, times(1)).fetchTerms();
    }

    @Test
    void givenNoCurrentTerm_whenRunningActivity_thenWrapsAsIllegalStateException() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(false, LocalDate.of(2023, 1, 1), 9,
                        new SejmPrints(0, null, "/term9/prints"),
                        LocalDate.of(2023, 10, 10))));
        var functions = newFunctions(collectService, sejmApiClient);

        assertThatThrownBy(() -> functions.collectCommittees(null, new FakeExecutionContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Failed to collect committee sittings")
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessageContaining("No current Sejm term found");
    }

    @Test
    void givenServiceThrows_whenCollectQuestions_thenWrapsAsIllegalStateException() {
        var collectService = mock(SejmCollectOperations.class);
        when(collectService.collectWrittenQuestions(eq(10), any(LocalDate.class)))
                .thenThrow(new RuntimeException("service failure"));
        var sejmApiClient = mock(SejmApiClient.class);
        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                        new SejmPrints(10, null, "/term10/prints"),
                        null)));
        var functions = newFunctions(collectService, sejmApiClient);

        assertThatThrownBy(() -> functions.collectQuestions(null, new FakeExecutionContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Failed to collect written questions")
                .hasCauseInstanceOf(RuntimeException.class)
                .cause()
                .hasMessage("service failure");
    }

    @Test
    void givenServiceThrows_whenCollectBills_thenReturnsZeroInsteadOfFailingOrchestration() {
        var collectService = mock(SejmCollectOperations.class);
        when(collectService.collectBills(eq(10), any(LocalDate.class)))
                .thenThrow(new RuntimeException("sejm api timeout"));
        var sejmApiClient = mock(SejmApiClient.class);
        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                        new SejmPrints(10, null, "/term10/prints"),
                        null)));
        var functions = newFunctions(collectService, sejmApiClient);

        var result = functions.collectBills(null, new FakeExecutionContext());

        assertThat(result.getCount()).isEqualTo(0);
        verify(collectService, times(1)).collectBills(eq(10), any(LocalDate.class));
    }

    @Test
    void givenOrchestrator_whenInvoked_thenCallsActivitiesWithRetryAndAggregatesCounts() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var functions = newFunctions(collectService, sejmApiClient);
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var votingTask = completedTask(activityResult(1));
        var committeesTask = completedTask(activityResult(2));
        var printsTask = completedTask(activityResult(3));
        var interpellationsTask = completedTask(activityResult(4));
        var questionsTask = completedTask(activityResult(5));
        var billsTask = completedTask(activityResult(6));
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
        when(orchestrationContext.allOf(any(List.class)))
                .thenReturn(new com.microsoft.durabletask.CompletedTask<>(List.of(1, 2, 3, 4, 5, 6)));

        var result = functions.runOrchestrator(orchestrationContext);

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
                eq(new EntityInstanceId(
                        COORDINATOR_ENTITY_NAME,
                        COORDINATOR_ENTITY_KEY)),
                eq("collectCompleted"),
                any());
    }

    @Test
    void givenAllCollectCoordinatorOperations_whenChecked_thenEachMapsToPublicEntityMethod() {
        var publicMethods = Arrays.stream(
                CollectCoordinatorEntity.class.getMethods())
                .map(Method::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        for (var op : COLLECT_COORDINATOR_OPERATIONS) {
            assertThat(publicMethods)
                    .as("enum %s must match a public method on CollectCoordinatorEntity", op)
                    .contains(op.toLowerCase());
        }
    }

    @Test
    void givenCoordinatorState_whenSerializedWithJackson_thenRoundTripsSuccessfully() throws Exception {
        var objectMapper = new ObjectMapper();
        var stateClass = Class.forName("onlexnet.infra.adapters.in.azurefunc.CollectCoordinatorState");
        var constructor = stateClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        var state = constructor.newInstance();
        var setRunning = stateClass.getDeclaredMethod("setRunning", boolean.class);
        setRunning.setAccessible(true);
        setRunning.invoke(state, true);
        var setPendingRequests = stateClass.getDeclaredMethod("setPendingRequests", int.class);
        setPendingRequests.setAccessible(true);
        setPendingRequests.invoke(state, 2);

        var json = objectMapper.writeValueAsString(state);
        var restored = objectMapper.readValue(json, stateClass);
        var isRunning = stateClass.getDeclaredMethod("isRunning");
        isRunning.setAccessible(true);
        var getPendingRequests = stateClass.getDeclaredMethod("getPendingRequests");
        getPendingRequests.setAccessible(true);

        assertThat(isRunning.invoke(restored)).isEqualTo(true);
        assertThat(getPendingRequests.invoke(restored)).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    private static CollectActivityResult activityResult(final int value) {
        var result = new CollectActivityResult();
        result.setCount(value);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> Task<T> completedTask(final T value) {
        var task = mock(Task.class);
        when(task.await()).thenReturn(value);
        return task;
    }

    private static SejmCollectFunctions newFunctions(
            final SejmCollectOperations collectService,
            final SejmApiClient sejmApiClient) {
        return new SejmCollectFunctions(collectService, sejmApiClient, newJsonValidator());
    }

    private static JsonValidator newJsonValidator() {
        var validator = new JsonValidator(new ObjectMapper().findAndRegisterModules());
        validator.init();
        return validator;
    }

    private static final class TestDurableClientContext extends DurableClientContext {

        private final DurableTaskClient client;
        private EntityInstanceId lastSignaledEntityId;
        private String lastEntityOperationName;
        private Object lastEntityPayload;

        private TestDurableClientContext(final boolean shouldFailSchedule) {
            this.client = new TestDurableTaskClient(shouldFailSchedule);
        }

        @Override
        public DurableTaskClient getClient() {
            return this.client;
        }

        @Override
        public HttpResponseMessage createCheckStatusResponse(
                final HttpRequestMessage<?> request,
                final String instanceId) {
            return request.createResponseBuilder(HttpStatus.ACCEPTED)
                    .header("Location", "https://localhost/runtime/status/" + instanceId)
                    .body(Map.of("instanceId", instanceId))
                    .build();
        }

        private void resetCapturedSignals() {
            this.lastSignaledEntityId = null;
            this.lastEntityOperationName = null;
            this.lastEntityPayload = null;
        }

        private final class TestDurableTaskClient extends DurableTaskClient {

            private final boolean shouldFailSchedule;
            private final DurableEntityClient entityClient = new TestDurableEntityClient();

            private TestDurableTaskClient(final boolean shouldFailSchedule) {
                this.shouldFailSchedule = shouldFailSchedule;
            }

            @Override
            public DurableEntityClient getEntities() {
                return this.entityClient;
            }

            @Override
            public String scheduleNewOrchestrationInstance(
                    final String orchestratorName,
                    final NewOrchestrationInstanceOptions options) {
                return schedule(orchestratorName, options);
            }

            @Override
            public String scheduleNewOrchestrationInstance(
                    final String orchestratorName,
                    final Object input) {
                return schedule(orchestratorName, input);
            }

            private String schedule(final String orchestratorName, final Object input) {
                if (this.shouldFailSchedule) {
                    throw new RuntimeException("durable client failure");
                }
                return "collect-instance-1";
            }

            @Override
            public void raiseEvent(final String instanceId, final String eventName,
                    final Object eventPayload) {
                if (this.shouldFailSchedule) {
                    throw new RuntimeException("durable client failure");
                }
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public OrchestrationMetadata getInstanceMetadata(final String instanceId,
                    final boolean getInputsAndOutputs) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public OrchestrationMetadata waitForInstanceStart(final String instanceId,
                    final Duration timeout,
                    final boolean getInputsAndOutputs) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public OrchestrationMetadata waitForInstanceCompletion(final String instanceId,
                    final Duration timeout,
                    final boolean getInputsAndOutputs) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public void terminate(final String instanceId, final Object output) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public OrchestrationStatusQueryResult queryInstances(final OrchestrationStatusQuery query) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public void createTaskHub(final boolean recreateIfExists) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public void deleteTaskHub() {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public PurgeResult purgeInstance(final String instanceId) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public PurgeResult purgeInstances(final PurgeInstanceCriteria criteria) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public String restartInstance(final String instanceId, final boolean restartWithNewId) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public void rewindInstance(final String instanceId, final String reason) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public void suspendInstance(final String instanceId, final String reason) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public void resumeInstance(final String instanceId, final String reason) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            private final class TestDurableEntityClient extends DurableEntityClient {

                private TestDurableEntityClient() {
                    super("test-entities");
                }

                @Override
                public void signalEntity(final EntityInstanceId entityId, final String operationName,
                        final Object input,
                        final com.microsoft.durabletask.SignalEntityOptions options) {
                    if (shouldFailSchedule) {
                        throw new RuntimeException("durable client failure");
                    }
                    lastSignaledEntityId = entityId;
                    lastEntityOperationName = operationName;
                    lastEntityPayload = input;
                }

                @Override
                public EntityMetadata getEntityMetadata(final EntityInstanceId entityId,
                        final boolean includeState) {
                    throw new UnsupportedOperationException("Not used by this unit test");
                }

                @Override
                public EntityQueryResult queryEntities(final EntityQuery query) {
                    throw new UnsupportedOperationException("Not used by this unit test");
                }

                @Override
                public CleanEntityStorageResult cleanEntityStorage(final CleanEntityStorageRequest request) {
                    throw new UnsupportedOperationException("Not used by this unit test");
                }
            }
        }
    }

    private static void assertActivityContract(final String methodName,
            final String expectedFunctionName) throws NoSuchMethodException {
        var method = SejmCollectFunctions.class.getDeclaredMethod(
                methodName,
                CollectActivityRequest.class,
                ExecutionContext.class);
        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(DurableActivityTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo(expectedFunctionName);
        assertThat(trigger).isNotNull();
        assertThat(trigger.name()).isEqualTo("request");
    }

    private static final class FakeExecutionContext implements ExecutionContext {

        @Override
        public Logger getLogger() {
            return Logger.getLogger(FakeExecutionContext.class.getName());
        }

        @Override
        public String getInvocationId() {
            return "collect-invocation";
        }

        @Override
        public String getFunctionName() {
            return SejmCollectFunctions.TIMER_FUNCTION_NAME;
        }
    }

    private static final class FakeHttpRequestMessage<T>
            implements HttpRequestMessage<Optional<T>> {

        private final Optional<T> body;

        private FakeHttpRequestMessage(final Optional<T> body) {
            this.body = body;
        }

        @Override
        public URI getUri() {
            return URI.create("https://localhost/api/Fun_CollectStart");
        }

        @Override
        public HttpMethod getHttpMethod() {
            return HttpMethod.POST;
        }

        @Override
        public Map<String, String> getHeaders() {
            return Map.of();
        }

        @Override
        public Map<String, String> getQueryParameters() {
            return Map.of();
        }

        @Override
        public Optional<T> getBody() {
            return this.body;
        }

        @Override
        public HttpResponseMessage.Builder createResponseBuilder(final HttpStatus status) {
            return new FakeHttpResponseBuilder().status(status);
        }

        @Override
        public HttpResponseMessage.Builder createResponseBuilder(final HttpStatusType status) {
            return new FakeHttpResponseBuilder().status(status);
        }
    }

    private static final class FakeHttpResponseBuilder
            implements HttpResponseMessage.Builder {

        private HttpStatusType status = HttpStatus.OK;
        private final java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
        private Object body;

        @Override
        public HttpResponseMessage.Builder status(final HttpStatusType value) {
            this.status = value;
            return this;
        }

        @Override
        public HttpResponseMessage.Builder header(final String key, final String value) {
            this.headers.put(key, value);
            return this;
        }

        @Override
        public HttpResponseMessage.Builder body(final Object value) {
            this.body = value;
            return this;
        }

        @Override
        public HttpResponseMessage build() {
            return new FakeHttpResponseMessage(this.status, this.headers, this.body);
        }
    }

    private static final class FakeHttpResponseMessage
            implements HttpResponseMessage {

        private final HttpStatusType status;
        private final Map<String, String> headers;
        private final Object body;

        private FakeHttpResponseMessage(final HttpStatusType status,
                final Map<String, String> headers,
                final Object body) {
            this.status = status;
            this.headers = Map.copyOf(headers);
            this.body = body;
        }

        @Override
        public HttpStatusType getStatus() {
            return this.status;
        }

        @Override
        public String getHeader(final String key) {
            return this.headers.get(key);
        }

        @Override
        public Object getBody() {
            return this.body;
        }
    }
}