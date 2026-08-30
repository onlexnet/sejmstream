package onlexnet.infra.adapters.out;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.azure.core.util.Context;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.app.ports.out.InterpellationPublishQueueMessage;
import onlexnet.app.ports.out.InterpellationPublishQueuePort;

/**
 * Azure Storage Queue adapter for INTERPELLATION publish jobs.
 */
@Component
public class AzureStorageInterpellationPublishQueue implements InterpellationPublishQueuePort {

    private final QueueClient publishQueueClient;
    private final QueueClient deadLetterQueueClient;
    private final ObjectMapper objectMapper;
    private final Object queueInitLock = new Object();
    private volatile boolean queuesInitialized;

    public AzureStorageInterpellationPublishQueue(
            // "DomainStorage" is dedicated to domain-logic storage (queues), kept separate from
            // "AzureWebJobsStorage" which is reserved for the Azure Functions host/runtime.
            @Value("${DomainStorage}") final String storageConnectionString,
            @Value("${interpellation.publish.queue.name:sejm-interpellations-publish}")
            String publishQueueName,
            @Value("${interpellation.publish.queue.dead-letter-name:sejm-interpellations-publish-deadletter}")
            String deadLetterQueueName,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        var normalizedConnectionString = storageConnectionString.trim();
        if (normalizedConnectionString.isEmpty()) {
            throw new IllegalStateException("Storage connection string must be configured");
        }

        this.publishQueueClient = new QueueClientBuilder()
            .connectionString(normalizedConnectionString)
            .queueName(publishQueueName)
            .buildClient();
        this.deadLetterQueueClient = new QueueClientBuilder()
            .connectionString(normalizedConnectionString)
            .queueName(deadLetterQueueName)
            .buildClient();
    }

    @Override
    public void enqueue(InterpellationPublishQueueMessage message, Duration visibilityDelay) {
        this.ensureQueuesInitialized();
        // Producer sends raw JSON text. The Functions host queue trigger must use host.json queues.messageEncoding="none".
        var payload = this.serialize(message);
        if (visibilityDelay == null || visibilityDelay.isZero() || visibilityDelay.isNegative()) {
            this.publishQueueClient.sendMessage(payload);
            return;
        }
        this.publishQueueClient.sendMessageWithResponse(payload, visibilityDelay, null, null, Context.NONE);
    }

    @Override
    public void enqueueDeadLetter(InterpellationPublishQueueMessage message) {
        this.ensureQueuesInitialized();
        // Keep dead-letter payload in the same raw JSON format as the primary queue.
        var payload = this.serialize(message);
        this.deadLetterQueueClient.sendMessage(payload);
    }

    private void ensureQueuesInitialized() {
        if (this.queuesInitialized) {
            return;
        }

        synchronized (this.queueInitLock) {
            if (this.queuesInitialized) {
                return;
            }
            this.publishQueueClient.createIfNotExists();
            this.deadLetterQueueClient.createIfNotExists();
            this.queuesInitialized = true;
        }
    }

    private String serialize(InterpellationPublishQueueMessage message) {
        try {
            return this.objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize queue message", exception);
        }
    }
}
