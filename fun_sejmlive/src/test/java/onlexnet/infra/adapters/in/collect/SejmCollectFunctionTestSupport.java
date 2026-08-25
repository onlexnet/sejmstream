package onlexnet.infra.adapters.in.collect;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.durabletask.CleanEntityStorageRequest;
import com.microsoft.durabletask.CleanEntityStorageResult;
import com.microsoft.durabletask.DurableEntityClient;
import com.microsoft.durabletask.DurableTaskClient;
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
import com.microsoft.durabletask.azurefunctions.DurableClientContext;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctionSupport;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResult;

final class SejmCollectFunctionTestSupport {

    private SejmCollectFunctionTestSupport() {
    }

    static SejmCollectFunctionSupport newSupport(
            final SejmCollectOperations collectService,
            final SejmApiClient sejmApiClient) {
        return new SejmCollectFunctionSupport(collectService, sejmApiClient, newJsonValidator());
    }

    static JsonValidator newJsonValidator() {
        var validator = new JsonValidator(new ObjectMapper().findAndRegisterModules());
        validator.init();
        return validator;
    }

    static CollectActivityResult activityResult(final int value) {
        var result = new CollectActivityResult();
        result.setCount(value);
        return result;
    }

    @SuppressWarnings("unchecked")
    static <T> Task<T> completedTask(final T value) {
        var task = mock(Task.class);
        when(task.await()).thenReturn(value);
        return task;
    }

    static List<Task<CollectActivityResult>> anyCollectActivityTasks() {
        return anyList();
    }

    static final class TestDurableClientContext extends DurableClientContext {

        private final DurableTaskClient client;
        EntityInstanceId lastSignaledEntityId;
        String lastEntityOperationName;
        Object lastEntityPayload;

        TestDurableClientContext(final boolean shouldFailSchedule) {
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

        void resetCapturedSignals() {
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

    static final class FakeExecutionContext implements ExecutionContext {

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

    static final class FakeHttpRequestMessage<T>
            implements HttpRequestMessage<Optional<T>> {

        private final Optional<T> body;

        FakeHttpRequestMessage(final Optional<T> body) {
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
