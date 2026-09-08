package onlexnet.infra.adapters.out.azure;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import onlexnet.app.ports.out.CollectOrchestratorEvent;
import onlexnet.app.ports.out.CollectOrchestratorEventPublisher;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestratorEventV1DTO;

/**
 * Azure Storage Queue adapter for collect orchestrator completion events.
 */
@Component
public class AzureStorageQueueCollectOrchestratorEventPublisher implements CollectOrchestratorEventPublisher {

    private final String queueName;
    private final String connectionString;
    private final ObjectMapper objectMapper;
    private final JsonValidator jsonValidator;

    private final Object queueClientLock = new Object();
    private final Object queueInitLock = new Object();
    private volatile @Nullable QueueClient queueClient;
    private volatile boolean queueInitialized;

    public AzureStorageQueueCollectOrchestratorEventPublisher(
            @Value("${collect.orchestrator.queue.name}") String queueName,
            @Value("${DomainStorage}") String connectionString,
            ObjectMapper objectMapper,
            JsonValidator jsonValidator) {
        this.queueName = normalizedRequiredConfig(queueName, "collect.orchestrator.queue.name");
        this.connectionString = normalizedRequiredConfig(connectionString, "DomainStorage");
        this.objectMapper = objectMapper;
        this.jsonValidator = jsonValidator;
    }

    @Override
    public void publish(CollectOrchestratorEvent event) {
        var validatedPayload = this.jsonValidator.validateToSend(
            JsonValidator.COLLECT_ORCHESTRATOR_EVENT_V1,
            toGeneratedV1Payload(event));
        var payload = serialize(validatedPayload);
        var client = queueClient();
        ensureQueueInitialized(client);
        client.sendMessage(payload);
    }

    private QueueClient queueClient() {
        var currentClient = this.queueClient;
        if (currentClient != null) {
            return currentClient;
        }

        synchronized (this.queueClientLock) {
            if (this.queueClient != null) {
                return this.queueClient;
            }

            this.queueClient = new QueueClientBuilder()
                .connectionString(this.connectionString)
                .queueName(this.queueName)
                .buildClient();
            return this.queueClient;
        }
    }

    private void ensureQueueInitialized(QueueClient client) {
        if (this.queueInitialized) {
            return;
        }

        synchronized (this.queueInitLock) {
            if (this.queueInitialized) {
                return;
            }
            client.createIfNotExists();
            this.queueInitialized = true;
        }
    }

    private static CollectOrchestratorEventV1DTO toGeneratedV1Payload(CollectOrchestratorEvent event) {
        return new CollectOrchestratorEventV1DTO()
                .orchestrationInstanceId(event.orchestrationInstanceId())
                .source(event.source())
                .termNum(event.termNum())
                .collectionDate(event.collectionDate())
                .countsByType(Map.copyOf(event.countsByType()));
    }

    private String serialize(CollectOrchestratorEventV1DTO event) {
        try {
            return this.objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize collect orchestrator event", exception);
        }
    }

    private static String normalizedRequiredConfig(@Nullable String value, String propertyName) {
        if (value == null) {
            throw new IllegalStateException("Missing required Storage Queue setting: " + propertyName);
        }
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException("Missing required Storage Queue setting: " + propertyName);
        }
        return normalized;
    }
}
