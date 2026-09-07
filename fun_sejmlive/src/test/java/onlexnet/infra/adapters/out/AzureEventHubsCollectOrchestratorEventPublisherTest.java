package onlexnet.infra.adapters.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import onlexnet.app.ports.out.CollectOrchestratorEvent;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;

class AzureEventHubsCollectOrchestratorEventPublisherTest {

    @Test
        void shouldPublishV1PayloadWithContractMetadata() {
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var jsonValidator = new JsonValidator(objectMapper);
        jsonValidator.init();

        var producer = mock(EventHubProducerClient.class);
        var batch = mock(EventDataBatch.class);
        var capturedEventData = new AtomicReference<EventData>();
        when(producer.createBatch()).thenReturn(batch);
        when(batch.tryAdd(any(EventData.class))).thenAnswer(invocation -> {
            capturedEventData.set(invocation.getArgument(0));
            return true;
        });

        var publisher = new AzureEventHubsCollectOrchestratorEventPublisher(
                "collect-events",
                "ns.servicebus.windows.net",
                "",
                objectMapper,
                jsonValidator);
        setField(publisher, "producerClient", producer);

        var event = new CollectOrchestratorEvent(
                "collect-instance-1",
                "timer",
                10,
                LocalDate.of(2026, 9, 7),
                Map.of("VOTING", 3));

        publisher.publish(event);

        verify(producer).send(batch);
        assertThat(capturedEventData)
                .hasValueSatisfying(eventData -> assertThat(eventData.getProperties())
                        .containsEntry(AzureEventHubsCollectOrchestratorEventPublisher.CONTRACT_VERSION_PROPERTY,
                                AzureEventHubsCollectOrchestratorEventPublisher.CONTRACT_VERSION)
                        .containsEntry(AzureEventHubsCollectOrchestratorEventPublisher.SCHEMA_ID_PROPERTY,
                                AzureEventHubsCollectOrchestratorEventPublisher.SCHEMA_ID));
    }

    @Test
        void shouldFailValidationBeforeSendingInvalidV1Payload() {
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var jsonValidator = new JsonValidator(objectMapper);
        jsonValidator.init();

        var producer = mock(EventHubProducerClient.class);
        var publisher = new AzureEventHubsCollectOrchestratorEventPublisher(
                "collect-events",
                "ns.servicebus.windows.net",
                "",
                objectMapper,
                jsonValidator);
        setField(publisher, "producerClient", producer);

        var invalidEvent = new CollectOrchestratorEvent(
                "collect-instance-1",
                "timer",
                10,
                LocalDate.of(2026, 9, 7),
                Map.of("VOTING", -1));

        assertThatThrownBy(() -> publisher.publish(invalidEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collect-orchestrator-event-v1.schema.json");
        verify(producer, never()).createBatch();
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