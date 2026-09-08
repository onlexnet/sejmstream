package onlexnet.infra.adapters.out.azure;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import jakarta.annotation.PreDestroy;
import onlexnet.app.ports.out.CollectOrchestratorEvent;
import onlexnet.app.ports.out.CollectOrchestratorEventPublisher;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestratorEventV1DTO;

/**
 * Event Hub adapter for collect orchestrator completion events.
 */
@Component
public class AzureEventHubsCollectOrchestratorEventPublisher implements CollectOrchestratorEventPublisher {

    static final String CONTRACT_VERSION = "v1";
    static final String CONTRACT_VERSION_PROPERTY = "collectEventContractVersion";
    static final String SCHEMA_ID = "https://onlexnet.dev/sejmstream/collect-flow/collect-orchestrator-event-v1.schema.json";
    static final String SCHEMA_ID_PROPERTY = "collectEventSchemaId";

    private final String eventHubName;
    private final String fullyQualifiedNamespace;
    private final String managedIdentityClientId;
    private final ObjectMapper objectMapper;
    private final JsonValidator jsonValidator;

    private final Object producerClientLock = new Object();
    private volatile @Nullable EventHubProducerClient producerClient;

    public AzureEventHubsCollectOrchestratorEventPublisher(
            @Value("${collect.orchestrator.event-hub.name}") String eventHubName,
            @Value("${collect.orchestrator.event-hub.fully-qualified-namespace}") String fullyQualifiedNamespace,
            @Value("${collect.orchestrator.event-hub.managed-identity-client-id:}") String managedIdentityClientId,
            ObjectMapper objectMapper,
            JsonValidator jsonValidator) {
        this.eventHubName = normalizedRequiredConfig(eventHubName, "collect.orchestrator.event-hub.name");
        this.fullyQualifiedNamespace = normalizedRequiredConfig(
                fullyQualifiedNamespace,
                "collect.orchestrator.event-hub.fully-qualified-namespace");
        this.managedIdentityClientId = managedIdentityClientId.trim();
        this.objectMapper = objectMapper;
        this.jsonValidator = jsonValidator;
    }

    @Override
    public void publish(CollectOrchestratorEvent event) {
        var validatedPayload = this.jsonValidator.validateToSend(
            JsonValidator.COLLECT_ORCHESTRATOR_EVENT_V1,
            toGeneratedV1Payload(event));
        var payload = serialize(validatedPayload);
        var producer = producerClient();
        var batch = producer.createBatch();
        var eventData = new EventData(payload);
        eventData.getProperties().put(CONTRACT_VERSION_PROPERTY, CONTRACT_VERSION);
        eventData.getProperties().put(SCHEMA_ID_PROPERTY, SCHEMA_ID);
        if (!batch.tryAdd(eventData)) {
            throw new IllegalStateException("Collect orchestrator event payload exceeds Event Hub batch size limits");
        }
        producer.send(batch);
    }

    @PreDestroy
    void close() {
        var currentClient = this.producerClient;
        if (currentClient == null) {
            return;
        }
        currentClient.close();
    }

    private EventHubProducerClient producerClient() {
        var currentClient = this.producerClient;
        if (currentClient != null) {
            return currentClient;
        }
        synchronized (this.producerClientLock) {
            if (this.producerClient != null) {
                return this.producerClient;
            }

            var credentialBuilder = new DefaultAzureCredentialBuilder();
            if (!this.managedIdentityClientId.isEmpty()) {
                credentialBuilder.managedIdentityClientId(this.managedIdentityClientId);
            }

            this.producerClient = new EventHubClientBuilder()
                    .credential(this.fullyQualifiedNamespace, this.eventHubName, credentialBuilder.build())
                    .buildProducerClient();
            return this.producerClient;
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

    private static String normalizedRequiredConfig(String value, String propertyName) {
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException("Missing required Event Hub setting: " + propertyName);
        }
        return normalized;
    }
}
