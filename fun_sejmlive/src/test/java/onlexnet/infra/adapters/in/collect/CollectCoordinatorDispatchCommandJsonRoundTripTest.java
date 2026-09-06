package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletion;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectCompletedCommand;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectFailedCommand;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorDispatchCommand;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorForceStartNextCommand;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorRequestCollectCommand;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectFailure;

class CollectCoordinatorDispatchCommandJsonRoundTripTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldRoundTripRequestCollectVariantThroughDispatchBaseType() throws Exception {
        var command = new CollectCoordinatorRequestCollectCommand();
        command.setSource("timer");

        assertRoundTrip(command, "requestCollect", restored -> {
            assertThat(restored).isInstanceOf(CollectCoordinatorRequestCollectCommand.class);
            var requestCollect = (CollectCoordinatorRequestCollectCommand) restored;
            assertThat(requestCollect.getSource()).isEqualTo("timer");
        });
    }

    @Test
    void shouldRoundTripCollectCompletedVariantThroughDispatchBaseType() throws Exception {
        var completion = new CollectCompletion();
        completion.setOrchestrationInstanceId("instance-1");

        var command = new CollectCoordinatorCollectCompletedCommand();
        command.setCompletion(completion);

        assertRoundTrip(command, "collectCompleted", restored -> {
            assertThat(restored).isInstanceOf(CollectCoordinatorCollectCompletedCommand.class);
            var collectCompleted = (CollectCoordinatorCollectCompletedCommand) restored;
            assertThat(collectCompleted.getCompletion()).isNotNull();
            assertThat(collectCompleted.getCompletion().getOrchestrationInstanceId()).isEqualTo("instance-1");
        });
    }

    @Test
    void shouldRoundTripCollectFailedVariantThroughDispatchBaseType() throws Exception {
        var failure = new CollectFailure();
        failure.setOrchestrationInstanceId("instance-2");
        failure.setMessage("boom");

        var command = new CollectCoordinatorCollectFailedCommand();
        command.setFailure(failure);

        assertRoundTrip(command, "collectFailed", restored -> {
            assertThat(restored).isInstanceOf(CollectCoordinatorCollectFailedCommand.class);
            var collectFailed = (CollectCoordinatorCollectFailedCommand) restored;
            assertThat(collectFailed.getFailure()).isNotNull();
            assertThat(collectFailed.getFailure().getOrchestrationInstanceId()).isEqualTo("instance-2");
            assertThat(collectFailed.getFailure().getMessage()).isEqualTo("boom");
        });
    }

    @Test
    void shouldRoundTripForceStartNextVariantThroughDispatchBaseType() throws Exception {
        var command = new CollectCoordinatorForceStartNextCommand();
        command.setSource("manual-recovery");

        assertRoundTrip(command, "forceStartNext", restored -> {
            assertThat(restored).isInstanceOf(CollectCoordinatorForceStartNextCommand.class);
            var forceStartNext = (CollectCoordinatorForceStartNextCommand) restored;
            assertThat(forceStartNext.getSource()).isEqualTo("manual-recovery");
        });
    }

    private static void assertRoundTrip(
            CollectCoordinatorDispatchCommand command,
            String expectedType,
            Consumer<CollectCoordinatorDispatchCommand> assertions) throws Exception {
        var json = OBJECT_MAPPER.writeValueAsString(command);
        assertThat(json).contains("\"type\":\"" + expectedType + "\"");

        var restored = OBJECT_MAPPER.readValue(json, CollectCoordinatorDispatchCommand.class);
        assertThat(restored.getType()).isEqualTo(expectedType);
        assertions.accept(restored);
    }
}
