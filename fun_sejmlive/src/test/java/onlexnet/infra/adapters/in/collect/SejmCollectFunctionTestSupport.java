package onlexnet.infra.adapters.in.collect;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import onlexnet.app.ports.out.SejmDailyDigestPersistence;
import onlexnet.infra.adapters.in.azurefunc.collectorchestrator.CollectActivityResultWire;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctionSupport;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;

final class SejmCollectFunctionTestSupport {

    private SejmCollectFunctionTestSupport() {
    }

    static SejmCollectFunctionSupport newSupport(
            SejmCollectOperations collectService,
            SejmApiClient sejmApiClient) {
        var digestPersistence = mock(SejmDailyDigestPersistence.class);
        return new SejmCollectFunctionSupport(collectService, sejmApiClient, digestPersistence, newJsonValidator());
    }

    static SejmCollectFunctionSupport newSupport(
            SejmCollectOperations collectService,
            SejmApiClient sejmApiClient,
            SejmDailyDigestPersistence digestPersistence) {
        return new SejmCollectFunctionSupport(collectService, sejmApiClient, digestPersistence, newJsonValidator());
    }

    static JsonValidator newJsonValidator() {
        var objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var validator = new JsonValidator(objectMapper);
        validator.init();
        return validator;
    }

    static CollectActivityResultWire activityResult(int value) {
        return new CollectActivityResultWire(value, null, null, List.of(), Map.of());
    }

    static CollectActivityResultWire activityResultWithSnapshot(
            int count,
            int termNum,
            LocalDate collectionDate,
            List<String> itemKeys,
            Map<String, String> interpellationFingerprints) {
        return new CollectActivityResultWire(
                count,
                termNum,
                collectionDate.toString(),
                itemKeys,
                interpellationFingerprints);
    }

    @SuppressWarnings("unchecked")
    static <T> Task<T> completedTask(T value) {
        var task = mock(Task.class);
        when(task.await()).thenReturn(value);
        return task;
    }

    static List<Task<CollectActivityResultWire>> anyCollectActivityTasks() {
        return anyList();
    }

    static final class TestDurableClientContext extends DurableClientContext {

        private final DurableTaskClient client;
        EntityInstanceId lastSignaledEntityId;
        String lastEntityOperationName;
        Object lastEntityPayload;

        TestDurableClientContext(boolean shouldFailSchedule) {
            this.client = new TestDurableTaskClient(shouldFailSchedule);
        }

        @Override
        public DurableTaskClient getClient() {
            return this.client;
        }

        @Override
        public HttpResponseMessage createCheckStatusResponse(
                HttpRequestMessage<?> request,
                String instanceId) {
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

            private TestDurableTaskClient(boolean shouldFailSchedule) {
                this.shouldFailSchedule = shouldFailSchedule;
            }

            @Override
            public DurableEntityClient getEntities() {
                return this.entityClient;
            }

            @Override
            public String scheduleNewOrchestrationInstance(
                    String orchestratorName,
                    NewOrchestrationInstanceOptions options) {
                return schedule(orchestratorName, options);
            }

            @Override
            public String scheduleNewOrchestrationInstance(
                    String orchestratorName,
                    Object input) {
                return schedule(orchestratorName, input);
            }

            private String schedule(String orchestratorName, Object input) {
                if (this.shouldFailSchedule) {
                    throw new RuntimeException("durable client failure");
                }
                return "collect-instance-1";
            }

            @Override
            public void raiseEvent(String instanceId, String eventName,
                    Object eventPayload) {
                if (this.shouldFailSchedule) {
                    throw new RuntimeException("durable client failure");
                }
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public OrchestrationMetadata getInstanceMetadata(String instanceId,
                    boolean getInputsAndOutputs) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public OrchestrationMetadata waitForInstanceStart(String instanceId,
                    Duration timeout,
                    boolean getInputsAndOutputs) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public OrchestrationMetadata waitForInstanceCompletion(String instanceId,
                    Duration timeout,
                    boolean getInputsAndOutputs) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public void terminate(String instanceId, Object output) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public OrchestrationStatusQueryResult queryInstances(OrchestrationStatusQuery query) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public void createTaskHub(boolean recreateIfExists) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public void deleteTaskHub() {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public PurgeResult purgeInstance(String instanceId) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public PurgeResult purgeInstances(PurgeInstanceCriteria criteria) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public String restartInstance(String instanceId, boolean restartWithNewId) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public void rewindInstance(String instanceId, String reason) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public void suspendInstance(String instanceId, String reason) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            @Override
            public void resumeInstance(String instanceId, String reason) {
                throw new UnsupportedOperationException("Not used by this unit test");
            }

            private final class TestDurableEntityClient extends DurableEntityClient {

                private TestDurableEntityClient() {
                    super("test-entities");
                }

                @Override
                public void signalEntity(EntityInstanceId entityId, String operationName,
                        Object input,
                        com.microsoft.durabletask.SignalEntityOptions options) {
                    if (shouldFailSchedule) {
                        throw new RuntimeException("durable client failure");
                    }
                    lastSignaledEntityId = entityId;
                    lastEntityOperationName = operationName;
                    lastEntityPayload = input;
                }

                @Override
                public EntityMetadata getEntityMetadata(EntityInstanceId entityId,
                        boolean includeState) {
                    throw new UnsupportedOperationException("Not used by this unit test");
                }

                @Override
                public EntityQueryResult queryEntities(EntityQuery query) {
                    throw new UnsupportedOperationException("Not used by this unit test");
                }

                @Override
                public CleanEntityStorageResult cleanEntityStorage(CleanEntityStorageRequest request) {
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

        FakeHttpRequestMessage(Optional<T> body) {
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
        public HttpResponseMessage.Builder createResponseBuilder(HttpStatus status) {
            return new FakeHttpResponseBuilder().status(status);
        }

        @Override
        public HttpResponseMessage.Builder createResponseBuilder(HttpStatusType status) {
            return new FakeHttpResponseBuilder().status(status);
        }
    }

    private static final class FakeHttpResponseBuilder
            implements HttpResponseMessage.Builder {

        private HttpStatusType status = HttpStatus.OK;
        private final java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
        private Object body;

        @Override
        public HttpResponseMessage.Builder status(HttpStatusType value) {
            this.status = value;
            return this;
        }

        @Override
        public HttpResponseMessage.Builder header(String key, String value) {
            this.headers.put(key, value);
            return this;
        }

        @Override
        public HttpResponseMessage.Builder body(Object value) {
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

        private FakeHttpResponseMessage(HttpStatusType status,
                Map<String, String> headers,
                Object body) {
            this.status = status;
            this.headers = Map.copyOf(headers);
            this.body = body;
        }

        @Override
        public HttpStatusType getStatus() {
            return this.status;
        }

        @Override
        public String getHeader(String key) {
            return this.headers.get(key);
        }

        @Override
        public Object getBody() {
            return this.body;
        }
    }
}
