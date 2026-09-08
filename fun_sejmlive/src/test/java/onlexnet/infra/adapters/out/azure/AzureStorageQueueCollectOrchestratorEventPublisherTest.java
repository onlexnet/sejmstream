package onlexnet.infra.adapters.out.azure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.azure.storage.queue.QueueClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import onlexnet.app.ports.out.CollectOrchestratorEvent;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;

class AzureStorageQueueCollectOrchestratorEventPublisherTest {

    @Test
    void shouldFailFastWhenQueueNameIsBlank() {
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var jsonValidator = new JsonValidator(objectMapper);
        jsonValidator.init();

        assertThatThrownBy(() -> new AzureStorageQueueCollectOrchestratorEventPublisher(
                "  ",
                "UseDevelopmentStorage=true",
                objectMapper,
                jsonValidator))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("collect.orchestrator.queue.name");
    }

    @Test
    void shouldPublishV1PayloadToQueue() {
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var jsonValidator = new JsonValidator(objectMapper);
        jsonValidator.init();

        var queueClient = mock(QueueClient.class);

        var publisher = new AzureStorageQueueCollectOrchestratorEventPublisher(
                "collect-events",
                "UseDevelopmentStorage=true",
                objectMapper,
                jsonValidator);
        setField(publisher, "queueClient", queueClient);
        setField(publisher, "queueInitialized", true);

        var event = new CollectOrchestratorEvent(
                "collect-instance-1",
                "timer",
                10,
                20260907,
                Map.of("VOTING", 3));

        publisher.publish(event);

        verify(queueClient).sendMessage(any(String.class));
        verify(queueClient, never()).createIfNotExists();
    }

    @Test
    void shouldFailValidationBeforeSendingInvalidV1Payload() {
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var jsonValidator = new JsonValidator(objectMapper);
        jsonValidator.init();

        var queueClient = mock(QueueClient.class);
        var publisher = new AzureStorageQueueCollectOrchestratorEventPublisher(
                "collect-events",
                "UseDevelopmentStorage=true",
                objectMapper,
                jsonValidator);
        setField(publisher, "queueClient", queueClient);
        setField(publisher, "queueInitialized", true);

        var invalidEvent = new CollectOrchestratorEvent(
                "collect-instance-1",
                "timer",
                10,
                20260907,
                Map.of("VOTING", -1));

        assertThatThrownBy(() -> publisher.publish(invalidEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collect-orchestrator-event-v1.schema.json");
        verify(queueClient, never()).sendMessage(any(String.class));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to set test field " + fieldName, exception);
        }
    }
}
