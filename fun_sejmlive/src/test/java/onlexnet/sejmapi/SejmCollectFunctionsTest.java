package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.TaskOptions;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;
import org.mockito.ArgumentCaptor;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmApiClient.SejmPrints;
import onlexnet.app.ports.out.SejmApiClient.SejmTerm;

@SpringBootTest(classes = Program.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
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
        var collectService = new RecordingCollectService();
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
        var collectService = new RecordingCollectService();
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
        var collectService = new RecordingCollectService();
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
        var collectService = new RecordingCollectService();
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
        var collectService = new RecordingCollectService();
        collectService.votingsCount = 11;
        collectService.billsCount = 22;
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
        assertThat(collectService.lastVotingsTerm).isEqualTo(10);
        assertThat(collectService.lastBillsTerm).isEqualTo(10);
                assertThat(collectService.lastVotingsDate)
                    .isBetween(beforeVotings, afterVotings);
                assertThat(collectService.lastBillsDate)
                    .isBetween(beforeBills, afterBills);
        verify(sejmApiClient, times(1)).fetchTerms();
    }

    @Test
    void givenNoCurrentTerm_whenRunningActivity_thenWrapsAsIllegalStateException() {
        var sejmApiClient = mock(SejmApiClient.class);
        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(false, LocalDate.of(2023, 1, 1), 9,
                        new SejmPrints(0, null, "/term9/prints"),
                        LocalDate.of(2023, 10, 10))));
        var functions = new SejmCollectFunctions(new RecordingCollectService(), sejmApiClient);

        assertThatThrownBy(() -> functions.collectCommittees(null, new FakeExecutionContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to collect committee sittings")
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessageContaining("No current Sejm term found");
    }

    @Test
    void givenServiceThrows_whenCollectQuestions_thenWrapsAsIllegalStateException() {
        var collectService = new RecordingCollectService();
        collectService.questionsFailure = new RuntimeException("service failure");
        var sejmApiClient = mock(SejmApiClient.class);
        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                        new SejmPrints(10, null, "/term10/prints"),
                        null)));
        var functions = new SejmCollectFunctions(collectService, sejmApiClient);

        assertThatThrownBy(() -> functions.collectQuestions(null, new FakeExecutionContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to collect written questions")
                .hasCauseInstanceOf(RuntimeException.class)
                .cause()
                .hasMessage("service failure");
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenOrchestrator_whenActivitiesSucceed_thenReturnsCountsAndUsesRetryPolicy() {
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var votingsTask = mockTask(1);
        var committeesTask = mockTask(2);
        var printsTask = mockTask(3);
        var interpellationsTask = mockTask(4);
        var questionsTask = mockTask(5);
        var billsTask = mockTask(6);
        when(orchestrationContext.callActivity(eq(SejmCollectFunctions.ACTIVITY_VOTINGS), isNull(),
                any(TaskOptions.class), eq(Integer.class))).thenReturn(votingsTask);
        when(orchestrationContext.callActivity(eq(SejmCollectFunctions.ACTIVITY_COMMITTEES), isNull(),
                any(TaskOptions.class), eq(Integer.class))).thenReturn(committeesTask);
        when(orchestrationContext.callActivity(eq(SejmCollectFunctions.ACTIVITY_PRINTS), isNull(),
                any(TaskOptions.class), eq(Integer.class))).thenReturn(printsTask);
        when(orchestrationContext.callActivity(eq(SejmCollectFunctions.ACTIVITY_INTERPELLATIONS), isNull(),
                any(TaskOptions.class), eq(Integer.class))).thenReturn(interpellationsTask);
        when(orchestrationContext.callActivity(eq(SejmCollectFunctions.ACTIVITY_QUESTIONS), isNull(),
                any(TaskOptions.class), eq(Integer.class))).thenReturn(questionsTask);
        when(orchestrationContext.callActivity(eq(SejmCollectFunctions.ACTIVITY_BILLS), isNull(),
                any(TaskOptions.class), eq(Integer.class))).thenReturn(billsTask);

        var result = new SejmCollectFunctions(new RecordingCollectService(), mock(SejmApiClient.class))
                .runOrchestrator(orchestrationContext);

        assertThat(result.countsByType()).isEqualTo(Map.of(
                "VOTING", 1,
                "COMMITTEE_SITTING", 2,
                "PRINT", 3,
                "INTERPELLATION", 4,
                "WRITTEN_QUESTION", 5,
                "BILL", 6));
        var optionsCaptor = ArgumentCaptor.forClass(TaskOptions.class);
        verify(orchestrationContext).callActivity(eq(SejmCollectFunctions.ACTIVITY_VOTINGS), isNull(),
                optionsCaptor.capture(), eq(Integer.class));
        var retryPolicy = optionsCaptor.getValue().getRetryPolicy();
        assertThat(retryPolicy).isNotNull();
        assertThat(retryPolicy.getMaxNumberOfAttempts()).isEqualTo(3);
        assertThat(retryPolicy.getFirstRetryInterval()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenOrchestrator_whenActivityReturnsNullCount_thenWrapsAsIllegalStateException() {
        var orchestrationContext = mock(TaskOrchestrationContext.class);
        var nullCountTask = mockTask(null);
        when(orchestrationContext.callActivity(eq(SejmCollectFunctions.ACTIVITY_VOTINGS), isNull(),
                any(TaskOptions.class), eq(Integer.class))).thenReturn(nullCountTask);

        var functions = new SejmCollectFunctions(new RecordingCollectService(), mock(SejmApiClient.class));

        assertThatThrownBy(() -> functions.runOrchestrator(orchestrationContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Collection orchestration failed")
                .hasCauseInstanceOf(NullPointerException.class)
                .cause()
                .hasMessageContaining("returned null count");
    }

    @SuppressWarnings("unchecked")
    private static Task<Integer> mockTask(Integer value) {
        var task = mock(Task.class);
        when(task.await()).thenReturn(value);
        return task;
    }

    private static final class RecordingCollectService extends SejmCollectService {

        private int votingsCount;
        private int committeesCount;
        private int printsCount;
        private int interpellationsCount;
        private int questionsCount;
        private int billsCount;

        private int lastVotingsTerm;
        private int lastBillsTerm;
        private LocalDate lastVotingsDate;
        private LocalDate lastBillsDate;

        private RuntimeException questionsFailure;

        private RecordingCollectService() {
            super(new NoopSejmApiClient(), new NoopRepository(), new ObjectMapper());
        }

        @Override
        public int collectVotings(final int termNum, final LocalDate date) {
            this.lastVotingsTerm = termNum;
            this.lastVotingsDate = date;
            return this.votingsCount;
        }

        @Override
        public int collectCommitteeSittings(final int termNum, final LocalDate date) {
            return this.committeesCount;
        }

        @Override
        public int collectPrints(final int termNum, final LocalDate date) {
            return this.printsCount;
        }

        @Override
        public int collectInterpellations(final int termNum, final LocalDate date) {
            return this.interpellationsCount;
        }

        @Override
        public int collectWrittenQuestions(final int termNum, final LocalDate date) {
            if (this.questionsFailure != null) {
                throw this.questionsFailure;
            }
            return this.questionsCount;
        }

        @Override
        public int collectBills(final int termNum, final LocalDate date) {
            this.lastBillsTerm = termNum;
            this.lastBillsDate = date;
            return this.billsCount;
        }
    }

    private static final class NoopRepository extends SejmDailyDigestRepository {

        private NoopRepository() {
            super(null);
        }
    }

    private static final class NoopSejmApiClient implements SejmApiClient {

        @Override
        public List<SejmTerm> fetchTerms() {
            return List.of();
        }

        @Override
        public List<VotingItem> fetchVotingsForDate(final int termNum, final LocalDate date) {
            return List.of();
        }

        @Override
        public List<CommitteeSittingItem> fetchCommitteeSittingsForDate(final int termNum,
                final LocalDate date) {
            return List.of();
        }

        @Override
        public List<PrintItem> fetchPrintsModifiedSince(final int termNum,
                final LocalDate since) {
            return List.of();
        }

        @Override
        public List<InterpellationItem> fetchInterpellationsModifiedSince(final int termNum,
                final LocalDateTime since) {
            return List.of();
        }

        @Override
        public List<WrittenQuestionItem> fetchWrittenQuestionsModifiedSince(final int termNum,
                final LocalDateTime since) {
            return List.of();
        }

        @Override
        public List<BillItem> fetchBillsReceivedSince(final int termNum, final LocalDate since) {
            return List.of();
        }
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