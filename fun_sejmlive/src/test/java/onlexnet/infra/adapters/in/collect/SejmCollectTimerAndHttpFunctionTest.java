package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.durabletask.EntityInstanceId;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.infra.adapters.in.azurefunc.collectcoordinator.CollectCoordinatorContractOperations;

class SejmCollectTimerAndHttpFunctionTest {

    private static final String COORDINATOR_ENTITY_NAME = "CollectCoordinator";
    private static final String COORDINATOR_ENTITY_KEY = "singleton";

    @Test
    void givenTimerTrigger_whenInvoked_thenSignalsCollectCoordinatorEntity() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);
        var clientCtx = new SejmCollectFunctionTestSupport.TestDurableClientContext(false);

        support.runTimer("timer", clientCtx, new SejmCollectFunctionTestSupport.FakeExecutionContext());

        assertThat(clientCtx.lastSignaledEntityId)
                .isEqualTo(new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY));
        assertThat(clientCtx.lastEntityOperationName)
                .isEqualTo(CollectCoordinatorContractOperations.REQUEST_COLLECT.methodName());
        assertThat(clientCtx.lastEntityPayload).isEqualTo("timer");
    }

    @Test
    void givenTimerTrigger_whenSchedulingFails_thenThrowsIllegalStateException() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);
        var clientCtx = new SejmCollectFunctionTestSupport.TestDurableClientContext(true);

        assertThatThrownBy(() -> support.runTimer("timer", clientCtx, new SejmCollectFunctionTestSupport.FakeExecutionContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to enqueue collection request")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void givenHttpStart_whenInvoked_thenReturnsAcceptedQueuedResponse() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);
        var clientCtx = new SejmCollectFunctionTestSupport.TestDurableClientContext(false);
        var request = new SejmCollectFunctionTestSupport.FakeHttpRequestMessage<String>(Optional.empty());

        var response = support.httpStart(request, clientCtx, new SejmCollectFunctionTestSupport.FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeader("Location")).isNull();
        assertThat(response.getBody()).isEqualTo(Map.of(
                "accepted", true,
                "coordinatorEntityId", new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY).toString(),
                "message", "Collect request was enqueued for serialized processing"));
    }

    @Test
    void givenHttpStart_whenSchedulingFails_thenReturnsInternalServerError() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);
        var clientCtx = new SejmCollectFunctionTestSupport.TestDurableClientContext(true);
        var request = new SejmCollectFunctionTestSupport.FakeHttpRequestMessage<String>(Optional.empty());

        var response = support.httpStart(request, clientCtx, new SejmCollectFunctionTestSupport.FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().toString()).contains("Failed to enqueue collection request");
    }

    @Test
    void givenMultipleTimerTriggers_whenInvoked_thenEachSignalsCoordinatorEntity() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);
        var clientCtx = new SejmCollectFunctionTestSupport.TestDurableClientContext(false);

        support.runTimer("timer-1", clientCtx, new SejmCollectFunctionTestSupport.FakeExecutionContext());
        clientCtx.resetCapturedSignals();
        support.runTimer("timer-2", clientCtx, new SejmCollectFunctionTestSupport.FakeExecutionContext());

        assertThat(clientCtx.lastSignaledEntityId)
                .isEqualTo(new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY));
        assertThat(clientCtx.lastEntityOperationName)
                .isEqualTo(CollectCoordinatorContractOperations.REQUEST_COLLECT.methodName());
        assertThat(clientCtx.lastEntityPayload).isEqualTo("timer");
    }
}
