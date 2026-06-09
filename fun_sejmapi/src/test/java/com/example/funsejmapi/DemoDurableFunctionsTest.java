package com.example.funsejmapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.net.URI;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.durabletask.DurableTaskClient;
import com.microsoft.durabletask.Task;
import com.microsoft.durabletask.NewOrchestrationInstanceOptions;
import com.microsoft.durabletask.OrchestrationMetadata;
import com.microsoft.durabletask.OrchestrationStatusQuery;
import com.microsoft.durabletask.OrchestrationStatusQueryResult;
import com.microsoft.durabletask.PurgeInstanceCriteria;
import com.microsoft.durabletask.PurgeResult;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;

class DemoDurableFunctionsTest {

    @Test
    void givenOpenApiFunction_whenCheckingTriggerContract_thenGetAnonymousAndRouteAreConfigured()
        throws NoSuchMethodException {
    var method = ApiDocumentationFunctions.class.getDeclaredMethod(
        "openApi",
        HttpRequestMessage.class);

    var functionName = method.getAnnotation(FunctionName.class);
    var trigger = method.getParameters()[0].getAnnotation(HttpTrigger.class);

    assertThat(functionName).isNotNull();
    assertThat(functionName.value())
        .isEqualTo(ApiDocumentationFunctions.OPENAPI_FUNCTION_NAME);
    assertThat(trigger).isNotNull();
    assertThat(trigger.methods()).containsExactly(HttpMethod.GET);
    assertThat(trigger.authLevel())
        .isEqualTo(AuthorizationLevel.ANONYMOUS);
    assertThat(trigger.route()).isEqualTo("openapi.json");
    }

    @Test
    void givenSwaggerUiFunction_whenInvoked_thenReturnsHtmlWithOpenApiReference() {
    var request = new FakeHttpRequestMessage<String>(Optional.empty());

    var response = new ApiDocumentationFunctions().swaggerUi(request);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeader("Content-Type"))
        .isEqualTo("text/html; charset=utf-8");
    assertThat(response.getBody().toString())
        .contains("swagger-ui")
        .contains("/api/openapi.json");
    }

    @Test
    void givenOpenApiFunction_whenInvoked_thenReturnsJsonWithStarterPath() {
    var request = new FakeHttpRequestMessage<String>(Optional.empty());

    var response = new ApiDocumentationFunctions().openApi(request);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeader("Content-Type"))
        .isEqualTo("application/json; charset=utf-8");
    assertThat(response.getBody().toString())
        .contains("\"openapi\": \"3.0.3\"")
        .contains("/api/SejmApiDemo_HttpStart");
    }

    @Test
    void givenHttpStarter_whenCheckingTriggerMethods_thenOnlyPostIsAllowed()
            throws NoSuchMethodException {
        var method = DemoDurableFunctions.class.getDeclaredMethod(
                "httpStart",
                HttpRequestMessage.class,
                DurableClientContext.class);

        var trigger = method.getParameters()[0].getAnnotation(HttpTrigger.class);

        assertThat(trigger).isNotNull();
        assertThat(trigger.methods()).containsExactly(HttpMethod.POST);
    }

    @Test
    void givenHttpStarter_whenCheckingTriggerAuthLevel_thenFunctionLevelIsRequired()
            throws NoSuchMethodException {
        var method = DemoDurableFunctions.class.getDeclaredMethod(
                "httpStart",
                HttpRequestMessage.class,
                DurableClientContext.class);

        var trigger = method.getParameters()[0].getAnnotation(HttpTrigger.class);

        assertThat(trigger).isNotNull();
        assertThat(trigger.authLevel())
            .isEqualTo(AuthorizationLevel.FUNCTION);
    }

    @Test
    void givenOrchestratorInput_whenRunningOrchestrator_thenCallsDemoActivityWithNormalizedContract() {
        var expectedResult = new DemoWorkflowResult(
                "instance-777",
                "demo-only",
                List.of("sample-row-1", "sample-row-2"));
        var state = new TestOrchestrationState(expectedResult);
        var orchestrationContext = state.createProxy(
                new DemoWorkflowRequest("   ", 0),
                "instance-777");

        var result = new DemoDurableFunctions().runOrchestrator(
                orchestrationContext);

        assertThat(state.capturedActivityName)
                .isEqualTo(DemoDurableFunctions.ACTIVITY_FUNCTION_NAME);
        assertThat(state.capturedActivityInput)
                .isEqualTo(new DemoWorkflowRequest("instance-777", 3));
        assertThat(state.capturedResultType)
            .isEqualTo(DemoWorkflowResult.class);
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void givenHttpStarter_whenInvoked_thenSchedulesAndReturnsAcceptedContract() {
        var functions = new DemoDurableFunctions();
        var request = new FakeHttpRequestMessage<>(
                Optional.of(new DemoWorkflowRequest("corr-200", 2)));
        var context = new TestDurableClientContext();

        var response = functions.httpStart(request, context);

        assertThat(context.capturedFunctionName)
                .isEqualTo(DemoDurableFunctions.ORCHESTRATOR_FUNCTION_NAME);
        assertThat(context.capturedInput)
                .isEqualTo(new DemoWorkflowRequest("corr-200", 2));
        assertThat(context.capturedInstanceId).isEqualTo("instance-123");
        assertThat(response.getStatusCode()).isEqualTo(202);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeader("Location"))
                .isEqualTo("https://localhost/runtime/status/instance-123");
        assertThat(response.getBody())
                .isEqualTo(Map.of("instanceId", "instance-123"));
    }

    @Test
    void givenValidRequest_whenRunningActivity_thenReturnsExplicitSampleContract() {
        var input = new DemoWorkflowRequest("corr-123", 2);

        var result = new DemoDurableFunctions().runDemoActivity(input, null);

        assertThat(result.correlationId()).isEqualTo("corr-123");
        assertThat(result.source()).isEqualTo("demo-only");
        assertThat(result.demoRows())
                .containsExactly("sample-row-1", "sample-row-2");
    }

    @Test
    void givenNullRequest_whenNormalizing_thenDefaultContractIsApplied() {
        var normalized = DemoWorkflowRequest.normalize(null);

        assertThat(normalized.correlationId()).startsWith("demo-");
        assertThat(normalized.sampleSize()).isEqualTo(3);
    }

    @Test
    void givenOutOfRangeSampleSize_whenNormalizing_thenSampleSizeIsBounded() {
        var normalized = DemoWorkflowRequest.normalize(
                new DemoWorkflowRequest("corr-456", 500));

        assertThat(normalized.correlationId()).isEqualTo("corr-456");
        assertThat(normalized.sampleSize()).isEqualTo(20);
    }

    @Test
    void givenBlankCorrelationAndNegativeSize_whenRunningActivity_thenRequestIsNormalized() {
        var input = new DemoWorkflowRequest("   ", -1);

        var result = new DemoDurableFunctions().runDemoActivity(input, null);

        assertThat(result.correlationId()).startsWith("demo-");
        assertThat(result.source()).isEqualTo("demo-only");
        assertThat(result.demoRows()).isEqualTo(List.of(
                "sample-row-1",
                "sample-row-2",
                "sample-row-3"));
    }

    @Test
    void givenNullRequest_whenNormalizingForOrchestrator_thenUsesInstanceIdFallback() {
        var normalized = DemoWorkflowRequest.normalizeForOrchestrator(
                null,
                "instance-777");

        assertThat(normalized.correlationId()).isEqualTo("instance-777");
        assertThat(normalized.sampleSize()).isEqualTo(3);
    }

    @Test
    void givenBlankCorrelation_whenNormalizingForOrchestrator_thenUsesInstanceIdFallback() {
        var normalized = DemoWorkflowRequest.normalizeForOrchestrator(
                new DemoWorkflowRequest("   ", 0),
                "instance-777");

        assertThat(normalized.correlationId()).isEqualTo("instance-777");
        assertThat(normalized.sampleSize()).isEqualTo(3);
    }

    @Test
    void givenBlankFallback_whenNormalizingForOrchestrator_thenUsesDemoUnknown() {
        var normalized = DemoWorkflowRequest.normalizeForOrchestrator(
                new DemoWorkflowRequest("   ", 2),
                " ");

        assertThat(normalized.correlationId()).isEqualTo("demo-unknown");
        assertThat(normalized.sampleSize()).isEqualTo(2);
    }

    private static final class TestDurableClientContext
            extends DurableClientContext {

        private final DurableTaskClient client = new TestDurableTaskClient();

        private String capturedFunctionName;
        private Object capturedInput;
        private String capturedInstanceId;

        @Override
        public DurableTaskClient getClient() {
            return this.client;
        }

        @Override
        public HttpResponseMessage createCheckStatusResponse(
                final HttpRequestMessage<?> request,
                final String instanceId) {
            this.capturedInstanceId = instanceId;
            return request.createResponseBuilder(HttpStatus.ACCEPTED)
                    .header(
                            "Location",
                            "https://localhost/runtime/status/" + instanceId)
                    .body(Map.of("instanceId", instanceId))
                    .build();
        }

        private final class TestDurableTaskClient extends DurableTaskClient {

            @Override
            public String scheduleNewOrchestrationInstance(
                    final String orchestratorName,
                    final NewOrchestrationInstanceOptions options) {
                throw new UnsupportedOperationException(
                        "Not used by this unit test");
            }

            @Override
            public String scheduleNewOrchestrationInstance(
                    final String orchestratorName,
                    final Object input) {
                capturedFunctionName = orchestratorName;
                capturedInput = input;
                return "instance-123";
            }

            @Override
            public void raiseEvent(
                    final String instanceId,
                    final String eventName,
                    final Object eventPayload) {
                throw new UnsupportedOperationException(
                        "Not used by this unit test");
            }

            @Override
            public OrchestrationMetadata getInstanceMetadata(
                    final String instanceId,
                    final boolean getInputsAndOutputs) {
                throw new UnsupportedOperationException(
                        "Not used by this unit test");
            }

            @Override
            public OrchestrationMetadata waitForInstanceStart(
                    final String instanceId,
                    final Duration timeout,
                    final boolean getInputsAndOutputs) {
                throw new UnsupportedOperationException(
                        "Not used by this unit test");
            }

            @Override
            public OrchestrationMetadata waitForInstanceCompletion(
                    final String instanceId,
                    final Duration timeout,
                    final boolean getInputsAndOutputs) {
                throw new UnsupportedOperationException(
                        "Not used by this unit test");
            }

            @Override
            public void terminate(
                    final String instanceId,
                    final Object output) {
                throw new UnsupportedOperationException(
                        "Not used by this unit test");
            }

            @Override
            public OrchestrationStatusQueryResult queryInstances(
                    final OrchestrationStatusQuery query) {
                throw new UnsupportedOperationException(
                        "Not used by this unit test");
            }

            @Override
            public void createTaskHub(final boolean recreateIfExists) {
                throw new UnsupportedOperationException(
                        "Not used by this unit test");
            }

            @Override
            public void deleteTaskHub() {
                throw new UnsupportedOperationException(
                        "Not used by this unit test");
            }

            @Override
            public PurgeResult purgeInstance(final String instanceId) {
                throw new UnsupportedOperationException(
                        "Not used by this unit test");
            }

            @Override
            public PurgeResult purgeInstances(
                    final PurgeInstanceCriteria criteria) {
                throw new UnsupportedOperationException(
                        "Not used by this unit test");
            }
        }
    }

    private static final class TestOrchestrationState {

        private final DemoWorkflowResult activityResult;

        private String capturedActivityName;
        private Object capturedActivityInput;
        private Object capturedResultType;

        TestOrchestrationState(final DemoWorkflowResult activityResult) {
            this.activityResult = activityResult;
        }

        TaskOrchestrationContext createProxy(
                final DemoWorkflowRequest input,
                final String instanceId) {
            InvocationHandler handler = (proxy, method, arguments) -> {
                return switch (method.getName()) {
                    case "getInput" -> input;
                    case "getInstanceId" -> instanceId;
                    case "callActivity" -> {
                        this.capturedActivityName = (String) arguments[0];
                        this.capturedActivityInput = arguments[1];
                        this.capturedResultType = arguments.length > 2
                                ? arguments[2]
                                : null;
                        yield completedTask(this.activityResult);
                    }
                    case "getName" -> DemoDurableFunctions.ORCHESTRATOR_FUNCTION_NAME;
                    case "getCurrentInstant" -> java.time.Instant.EPOCH;
                    case "getIsReplaying" -> false;
                    default -> throw new UnsupportedOperationException(
                            "Not used by this unit test: " + method.getName());
                };
            };

            return (TaskOrchestrationContext) Proxy.newProxyInstance(
                    TaskOrchestrationContext.class.getClassLoader(),
                    new Class<?>[] {TaskOrchestrationContext.class},
                    handler);
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> Task<V> completedTask(final V value) {
        try {
            var taskClass = Class.forName(
                    "com.microsoft.durabletask.TaskOrchestrationExecutor$"
                            + "ContextImplTask$CompletableTask");
            var constructor = taskClass.getDeclaredConstructor(
                    Class.forName(
                            "com.microsoft.durabletask.TaskOrchestrationExecutor$"
                                    + "ContextImplTask"),
                    CompletableFuture.class);
            constructor.setAccessible(true);
            return (Task<V>) constructor.newInstance(
                    new Object[] {
                            null,
                            CompletableFuture.completedFuture(value) });
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Unable to create completed durable task", exception);
        }
    }

    private static final class FakeHttpRequestMessage<T>
            implements HttpRequestMessage<Optional<T>> {

        private final Optional<T> body;

        FakeHttpRequestMessage(final Optional<T> body) {
            this.body = body;
        }

        @Override
        public URI getUri() {
            return URI.create("https://localhost/api/SejmApiDemo_HttpStart");
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
        public HttpResponseMessage.Builder createResponseBuilder(
                final HttpStatus status) {
            return new FakeHttpResponseBuilder().status(status);
        }

        @Override
        public HttpResponseMessage.Builder createResponseBuilder(
                final HttpStatusType status) {
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
        public HttpResponseMessage.Builder header(
                final String key,
                final String value) {
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
            return new FakeHttpResponseMessage(this.status, this.headers,
                    this.body);
        }
    }

    private static final class FakeHttpResponseMessage
            implements HttpResponseMessage {

        private final HttpStatusType status;
        private final Map<String, String> headers;
        private final Object body;

        FakeHttpResponseMessage(
                final HttpStatusType status,
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
