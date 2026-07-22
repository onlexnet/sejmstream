package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

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
import com.microsoft.durabletask.DurableTaskClient;
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
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmApiClient.SejmPrints;
import onlexnet.app.ports.out.SejmApiClient.SejmTerm;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.infra.adapters.out.SejmCollectService;
import onlexnet.testsupport.AppTest;

@AppTest
class SejmCollectFunctionsTest {

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
    assertThat(this.applicationContext.getBean(SejmCollectFunctions.class))
        .isSameAs(this.sejmCollectFunctions);
    assertThat(this.applicationContext.getBean(SejmCollectService.class))
        .isSameAs(this.sejmCollectService);
    assertThat(this.applicationContext.getBean(SejmApiClient.class))
        .isSameAs(this.sejmApiClient);
    }

    @Test
    void givenTimerFunction_whenCheckingTriggerContract_thenRunsDailyAt1630()
            throws NoSuchMethodException {
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
    void givenTimerTrigger_whenInvoked_thenSchedulesCollectOrchestrator() {
            var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var functions = new SejmCollectFunctions(collectService, sejmApiClient);
        var durableContext = new TestDurableClientContext(false);

        functions.runTimer("timer", durableContext, new FakeExecutionContext());

        assertThat(durableContext.capturedFunctionName)
                .isEqualTo(SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME);
        assertThat(durableContext.capturedInput).isNull();
    }

    @Test
    void givenTimerTrigger_whenSchedulingFails_thenThrowsIllegalStateException() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var functions = new SejmCollectFunctions(collectService, sejmApiClient);
        var durableContext = new TestDurableClientContext(true);

        assertThatThrownBy(
                () -> functions.runTimer("timer", durableContext, new FakeExecutionContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to start collection orchestration")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void givenHttpStart_whenInvoked_thenReturnsAcceptedStatusEndpointsResponse() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var functions = new SejmCollectFunctions(collectService, sejmApiClient);
        var durableContext = new TestDurableClientContext(false);
        var request = new FakeHttpRequestMessage<String>(Optional.empty());

        var response = functions.httpStart(request, durableContext, new FakeExecutionContext());

        assertThat(durableContext.capturedFunctionName)
                .isEqualTo(SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeader("Location"))
                .isEqualTo("https://localhost/runtime/status/collect-instance-1");
        assertThat(response.getBody()).isEqualTo(Map.of("instanceId", "collect-instance-1"));
    }

    @Test
    void givenHttpStart_whenSchedulingFails_thenReturnsInternalServerError() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var functions = new SejmCollectFunctions(collectService, sejmApiClient);
        var durableContext = new TestDurableClientContext(true);
        var request = new FakeHttpRequestMessage<String>(Optional.empty());

        var response = functions.httpStart(request, durableContext, new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().toString()).contains("Failed to start collection");
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
        var functions = new SejmCollectFunctions(collectService, sejmApiClient);

                var beforeVotings = LocalDate.now();
        var votingResult = functions.collectVotings(null, new FakeExecutionContext());
                var afterVotings = LocalDate.now();

                var beforeBills = LocalDate.now();
        var billsResult = functions.collectBills(null, new FakeExecutionContext());
                var afterBills = LocalDate.now();

        assertThat(votingResult).isEqualTo(11);
        assertThat(billsResult).isEqualTo(22);
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
        var functions = new SejmCollectFunctions(collectService, sejmApiClient);

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
        var functions = new SejmCollectFunctions(collectService, sejmApiClient);

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
        var functions = new SejmCollectFunctions(collectService, sejmApiClient);

        var result = functions.collectBills(null, new FakeExecutionContext());

        assertThat(result).isEqualTo(0);
        verify(collectService, times(1)).collectBills(eq(10), any(LocalDate.class));
    }

    @Test
    void givenOrchestrator_whenInvoked_thenCallsActivitiesWithRetryAndAggregatesCounts() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var functions = new SejmCollectFunctions(collectService, sejmApiClient);
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var votingTask = completedTask(1);
        var committeesTask = completedTask(2);
        var printsTask = completedTask(3);
        var interpellationsTask = completedTask(4);
        var questionsTask = completedTask(5);
        var billsTask = completedTask(6);

        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_VOTINGS),
                eq(null),
                any(TaskOptions.class),
                eq(Integer.class))).thenReturn(votingTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_COMMITTEES),
                eq(null),
                any(TaskOptions.class),
                eq(Integer.class))).thenReturn(committeesTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_PRINTS),
                eq(null),
                any(TaskOptions.class),
                eq(Integer.class))).thenReturn(printsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_INTERPELLATIONS),
                eq(null),
                any(TaskOptions.class),
                eq(Integer.class))).thenReturn(interpellationsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_QUESTIONS),
                eq(null),
                any(TaskOptions.class),
                eq(Integer.class))).thenReturn(questionsTask);
        when(orchestrationContext.callActivity(
                eq(SejmCollectFunctions.ACTIVITY_BILLS),
                eq(null),
                any(TaskOptions.class),
                eq(Integer.class))).thenReturn(billsTask);

        var result = functions.runOrchestrator(orchestrationContext);

        assertThat(result.countsByType()).containsEntry("VOTING", 1);
        assertThat(result.countsByType()).containsEntry("COMMITTEE_SITTING", 2);
        assertThat(result.countsByType()).containsEntry("PRINT", 3);
        assertThat(result.countsByType()).containsEntry("INTERPELLATION", 4);
        assertThat(result.countsByType()).containsEntry("WRITTEN_QUESTION", 5);
        assertThat(result.countsByType()).containsEntry("BILL", 6);

        verify(orchestrationContext, times(6)).callActivity(
                any(String.class),
                eq(null),
                any(TaskOptions.class),
                eq(Integer.class));
    }

    @SuppressWarnings("unchecked")
    private static Task<Integer> completedTask(final int value) {
        var task = mock(Task.class);
        when(task.await()).thenReturn(value);
        return task;
    }

    private static final class TestDurableClientContext extends DurableClientContext {

        private final DurableTaskClient client;
        private String capturedFunctionName;
        private Object capturedInput;

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

        private final class TestDurableTaskClient extends DurableTaskClient {

            private final boolean shouldFailSchedule;

            private TestDurableTaskClient(final boolean shouldFailSchedule) {
                this.shouldFailSchedule = shouldFailSchedule;
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
                capturedFunctionName = orchestratorName;
                capturedInput = input;
                return "collect-instance-1";
            }

            @Override
            public void raiseEvent(final String instanceId, final String eventName,
                    final Object eventPayload) {
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
        }
    }

    private static void assertActivityContract(final String methodName,
            final String expectedFunctionName) throws NoSuchMethodException {
        var method = SejmCollectFunctions.class.getDeclaredMethod(
                methodName,
                String.class,
                ExecutionContext.class);
        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(DurableActivityTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo(expectedFunctionName);
        assertThat(trigger).isNotNull();
        assertThat(trigger.name()).isEqualTo("ignored");
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
        private final java.util.LinkedHashMap<String, String> headers =
                new java.util.LinkedHashMap<>();
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