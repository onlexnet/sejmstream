package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.durabletask.EntityInstanceId;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctionSupport;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectTimerFunction;
import onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator.DailyDigestIngestionCoordinatorContractOperations;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorRequestCollectCommandDTO;

class SejmCollectTimerAndHttpFunctionTest {

    private static final String COORDINATOR_ENTITY_NAME = "collectcoordinator";
    private static final String COORDINATOR_ENTITY_KEY = "singleton";

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-11-05T00:00:00Z",
            "2026-07-04T23:00:00Z",
            "2026-03-29T00:00:00Z",
            "2026-03-29T23:00:00Z",
            "2026-10-24T23:00:00Z",
            "2026-10-26T00:00:00Z"
    })
    void shouldCollectAtOneAmWarsawInWinterSummerAndAcrossClockChanges(String instant) {
        var clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        var function = new SejmCollectTimerFunction(new SejmCollectFunctionSupport(), clock);
        var clientCtx = new SejmCollectFunctionTestSupport.TestDurableClientContext(false);

        function.runTimer("timer", clientCtx, new SejmCollectFunctionTestSupport.FakeExecutionContext());

        assertThat(clientCtx.lastSignaledEntityId)
                .isEqualTo(new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY));
        assertThat(clientCtx.lastEntityPayload).isInstanceOf(CollectCoordinatorRequestCollectCommandDTO.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-11-04T23:00:00Z",
            "2026-07-05T00:00:00Z",
            "2026-03-28T23:00:00Z",
            "2026-03-30T00:00:00Z",
            "2026-10-25T00:00:00Z",
            "2026-10-25T23:00:00Z"
    })
    void shouldSkipOtherUtcCandidateWithoutSignalingDurable(String instant) {
        var clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        var function = new SejmCollectTimerFunction(new SejmCollectFunctionSupport(), clock);
        var clientCtx = new SejmCollectFunctionTestSupport.TestDurableClientContext(false);

        function.runTimer("timer", clientCtx, new SejmCollectFunctionTestSupport.FakeExecutionContext());

        assertThat(clientCtx.lastSignaledEntityId).isNull();
    }

    @Test
    void givenTimerTrigger_whenInvoked_thenSignalsDailyDigestIngestionCoordinatorEntity() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);
        var clientCtx = new SejmCollectFunctionTestSupport.TestDurableClientContext(false);

        support.runTimer("timer", clientCtx, new SejmCollectFunctionTestSupport.FakeExecutionContext());

        assertThat(clientCtx.lastSignaledEntityId)
                .isEqualTo(new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY));
        assertThat(clientCtx.lastEntityOperationName)
            .isEqualTo(DailyDigestIngestionCoordinatorContractOperations.DISPATCH.methodName());
        assertThat(clientCtx.lastEntityPayload).isInstanceOf(CollectCoordinatorRequestCollectCommandDTO.class);
        var command = (CollectCoordinatorRequestCollectCommandDTO) clientCtx.lastEntityPayload;
        assertThat(command.getSource()).isEqualTo("timer");
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
        assertThat(clientCtx.lastSignaledEntityId)
            .isEqualTo(new EntityInstanceId(COORDINATOR_ENTITY_NAME, COORDINATOR_ENTITY_KEY));
        assertThat(clientCtx.lastEntityOperationName)
            .isEqualTo(DailyDigestIngestionCoordinatorContractOperations.DISPATCH.methodName());
        assertThat(clientCtx.lastEntityPayload).isInstanceOf(CollectCoordinatorRequestCollectCommandDTO.class);
        var command = (CollectCoordinatorRequestCollectCommandDTO) clientCtx.lastEntityPayload;
        assertThat(command.getSource()).isEqualTo("http");
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
            .isEqualTo(DailyDigestIngestionCoordinatorContractOperations.DISPATCH.methodName());
        assertThat(clientCtx.lastEntityPayload).isInstanceOf(CollectCoordinatorRequestCollectCommandDTO.class);
        var command = (CollectCoordinatorRequestCollectCommandDTO) clientCtx.lastEntityPayload;
        assertThat(command.getSource()).isEqualTo("timer");
    }
}
