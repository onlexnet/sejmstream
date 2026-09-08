package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.durabletask.EntityInstanceId;

import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestratorEventV1DTO;
import onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotCollectedEvent;
import onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotCollectedEventMaterializer;
import onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotReconcilerCollectEventFunction;
import onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotReconcilerContractOperations;

class TermSnapshotReconcilerCollectEventFunctionTest {

    @Test
    void givenValidCollectEventPayload_whenTriggered_thenSignalsTermSnapshotEntityWithNumericTermKey() throws Exception {
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var jsonValidator = SejmCollectFunctionTestSupport.newJsonValidator();
        var materializer = mock(TermSnapshotCollectedEventMaterializer.class);

        var collectEventPayload = new CollectOrchestratorEventV1DTO()
                .orchestrationInstanceId("collect-instance-1")
                .source("timer")
                .termNum(10)
                .collectionDate(20260907)
                .countsByType(Map.of("VOTING", 2));

        var snapshotEvent = new TermSnapshotCollectedEvent(
                20260907,
                "timer",
                "collect-instance-1",
                Map.of("77", "hash-1"),
                List.of("301"),
                List.of("401"),
                List.of("501"));
        when(materializer.materialize(any())).thenReturn(snapshotEvent);

        var function = new TermSnapshotReconcilerCollectEventFunction(objectMapper, jsonValidator, materializer);
        var durableClientContext = new SejmCollectFunctionTestSupport.TestDurableClientContext(false);

        function.runFromCollectEvent(
                objectMapper.writeValueAsString(collectEventPayload),
                durableClientContext,
                new SejmCollectFunctionTestSupport.FakeExecutionContext());

        assertThat(durableClientContext.lastSignaledEntityId)
                .isEqualTo(new EntityInstanceId(SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME, "10"));
        assertThat(durableClientContext.lastEntityOperationName)
                .isEqualTo(TermSnapshotReconcilerContractOperations.TERM_SNAPSHOT_COLLECTED.methodName());
        assertThat(durableClientContext.lastEntityPayload).isEqualTo(snapshotEvent);

        verify(materializer).materialize(argThat(event ->
                "collect-instance-1".equals(event.getOrchestrationInstanceId())
                        && "timer".equals(event.getSource())
                        && Integer.valueOf(10).equals(event.getTermNum())
                        && Integer.valueOf(20260907).equals(event.getCollectionDate())));
    }

    @Test
    void givenMalformedJsonPayload_whenTriggered_thenSkipsWithoutSignal() {
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var jsonValidator = SejmCollectFunctionTestSupport.newJsonValidator();
        var materializer = mock(TermSnapshotCollectedEventMaterializer.class);
        var function = new TermSnapshotReconcilerCollectEventFunction(objectMapper, jsonValidator, materializer);
        var durableClientContext = new SejmCollectFunctionTestSupport.TestDurableClientContext(false);

        function.runFromCollectEvent(
                "{not-json}",
                durableClientContext,
                new SejmCollectFunctionTestSupport.FakeExecutionContext());

        assertThat(durableClientContext.lastSignaledEntityId).isNull();
        assertThat(durableClientContext.lastEntityOperationName).isNull();
        assertThat(durableClientContext.lastEntityPayload).isNull();
        verify(materializer, never()).materialize(any());
    }

    @Test
    void givenSchemaInvalidCollectEventPayload_whenTriggered_thenSkipsWithoutSignal() {
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var jsonValidator = SejmCollectFunctionTestSupport.newJsonValidator();
        var materializer = mock(TermSnapshotCollectedEventMaterializer.class);
        var function = new TermSnapshotReconcilerCollectEventFunction(objectMapper, jsonValidator, materializer);
        var durableClientContext = new SejmCollectFunctionTestSupport.TestDurableClientContext(false);

        var schemaInvalidPayload = """
                {
                  "orchestrationInstanceId": "collect-instance-2",
                  "termNum": 10,
                  "collectionDate": 20260907,
                  "countsByType": {
                    "VOTING": 1
                  }
                }
                """;

        function.runFromCollectEvent(
                schemaInvalidPayload,
                durableClientContext,
                new SejmCollectFunctionTestSupport.FakeExecutionContext());

        assertThat(durableClientContext.lastSignaledEntityId).isNull();
        assertThat(durableClientContext.lastEntityOperationName).isNull();
        assertThat(durableClientContext.lastEntityPayload).isNull();
        verify(materializer, never()).materialize(any());
    }

    @Test
    void givenMaterializationFailure_whenTriggered_thenThrowsToEnableRetry() throws Exception {
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var jsonValidator = SejmCollectFunctionTestSupport.newJsonValidator();
        var materializer = mock(TermSnapshotCollectedEventMaterializer.class);
        when(materializer.materialize(any())).thenThrow(new IllegalStateException("digest rows unavailable"));

        var function = new TermSnapshotReconcilerCollectEventFunction(objectMapper, jsonValidator, materializer);
        var durableClientContext = new SejmCollectFunctionTestSupport.TestDurableClientContext(false);

        var payload = new CollectOrchestratorEventV1DTO()
                .orchestrationInstanceId("collect-instance-3")
                .source("timer")
                .termNum(11)
                .collectionDate(20260908)
                .countsByType(Map.of("PRINT", 1));

        assertThatThrownBy(() -> function.runFromCollectEvent(
                objectMapper.writeValueAsString(payload),
                durableClientContext,
                new SejmCollectFunctionTestSupport.FakeExecutionContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to forward collect event to term snapshot reconciler")
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessageContaining("digest rows unavailable");

        assertThat(durableClientContext.lastSignaledEntityId).isNull();
    }
}
