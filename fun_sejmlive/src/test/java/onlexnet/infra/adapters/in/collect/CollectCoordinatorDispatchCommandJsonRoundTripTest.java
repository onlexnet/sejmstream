package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletionDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectCompletedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectFailedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorDispatchCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorForceStartNextCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorRequestCollectCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectFailureDTO;

class CollectCoordinatorDispatchCommandJsonRoundTripTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldRoundTripRequestCollectVariantThroughDispatchBaseType() throws Exception {
        var command = new CollectCoordinatorRequestCollectCommandDTO();
        command.setSource("timer");

        assertRoundTrip(command, "requestCollect", restored -> {
            assertThat(restored).isInstanceOf(CollectCoordinatorRequestCollectCommandDTO.class);
            var requestCollect = (CollectCoordinatorRequestCollectCommandDTO) restored;
            assertThat(requestCollect.getSource()).isEqualTo("timer");
        });
    }

    @Test
    void shouldRoundTripCollectCompletedVariantThroughDispatchBaseType() throws Exception {
        var completion = new CollectCompletionDTO();
        completion.setOrchestrationInstanceId("instance-1");

        var command = new CollectCoordinatorCollectCompletedCommandDTO();
        command.setCompletion(completion);

        assertRoundTrip(command, "collectCompleted", restored -> {
            assertThat(restored).isInstanceOf(CollectCoordinatorCollectCompletedCommandDTO.class);
            var collectCompleted = (CollectCoordinatorCollectCompletedCommandDTO) restored;
            assertThat(collectCompleted.getCompletion()).isNotNull();
            assertThat(collectCompleted.getCompletion().getOrchestrationInstanceId()).isEqualTo("instance-1");
        });
    }

    @Test
    void shouldRoundTripCollectFailedVariantThroughDispatchBaseType() throws Exception {
        var failure = new CollectFailureDTO();
        failure.setOrchestrationInstanceId("instance-2");
        failure.setMessage("boom");

        var command = new CollectCoordinatorCollectFailedCommandDTO();
        command.setFailure(failure);

        assertRoundTrip(command, "collectFailed", restored -> {
            assertThat(restored).isInstanceOf(CollectCoordinatorCollectFailedCommandDTO.class);
            var collectFailed = (CollectCoordinatorCollectFailedCommandDTO) restored;
            assertThat(collectFailed.getFailure()).isNotNull();
            assertThat(collectFailed.getFailure().getOrchestrationInstanceId()).isEqualTo("instance-2");
            assertThat(collectFailed.getFailure().getMessage()).isEqualTo("boom");
        });
    }

    @Test
    void shouldRoundTripForceStartNextVariantThroughDispatchBaseType() throws Exception {
        var command = new CollectCoordinatorForceStartNextCommandDTO();
        command.setSource("manual-recovery");

        assertRoundTrip(command, "forceStartNext", restored -> {
            assertThat(restored).isInstanceOf(CollectCoordinatorForceStartNextCommandDTO.class);
            var forceStartNext = (CollectCoordinatorForceStartNextCommandDTO) restored;
            assertThat(forceStartNext.getSource()).isEqualTo("manual-recovery");
        });
    }

    private static void assertRoundTrip(
            CollectCoordinatorDispatchCommandDTO command,
            String expectedType,
            Consumer<CollectCoordinatorDispatchCommandDTO> assertions) throws Exception {
        var json = OBJECT_MAPPER.writeValueAsString(command);
        assertThat(json).contains("\"type\":\"" + expectedType + "\"");

        var restored = OBJECT_MAPPER.readValue(json, CollectCoordinatorDispatchCommandDTO.class);
        assertThat(restored.getType()).isEqualTo(expectedType);
        assertions.accept(restored);
    }
}
