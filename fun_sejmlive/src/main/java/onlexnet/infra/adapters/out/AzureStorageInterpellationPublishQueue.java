package onlexnet.infra.adapters.out;

import java.time.Duration;
import java.util.Objects;

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

    public AzureStorageInterpellationPublishQueue(
            // "Storage" is dedicated to domain-logic storage (queues), kept separate from
            // "AzureWebJobsStorage" which is reserved for the Azure Functions host/runtime.
            @Value("${Storage}") final String storageConnectionString,
            @Value("${interpellation.publish.queue.name:sejm-interpellations-publish}")
            final String publishQueueName,
            @Value("${interpellation.publish.queue.dead-letter-name:sejm-interpellations-publish-deadletter}")
            final String deadLetterQueueName,
            final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        var normalizedConnectionString = Objects.requireNonNull(storageConnectionString, "Storage connection string must be configured").trim();
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
        this.publishQueueClient.createIfNotExists();
        this.deadLetterQueueClient.createIfNotExists();
    }

    @Override
    public void enqueue(final InterpellationPublishQueueMessage message, final Duration visibilityDelay) {
        // Producer sends raw JSON text. The Functions host queue trigger must use host.json queues.messageEncoding="none".
        var payload = this.serialize(message);
        if (visibilityDelay == null || visibilityDelay.isZero() || visibilityDelay.isNegative()) {
            this.publishQueueClient.sendMessage(payload);
            return;
        }
        this.publishQueueClient.sendMessageWithResponse(payload, visibilityDelay, null, null, Context.NONE);
    }

    @Override
    public void enqueueDeadLetter(final InterpellationPublishQueueMessage message) {
        // Keep dead-letter payload in the same raw JSON format as the primary queue.
        var payload = this.serialize(message);
        this.deadLetterQueueClient.sendMessage(payload);
    }

    private String serialize(final InterpellationPublishQueueMessage message) {
        try {
            return this.objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize queue message", exception);
        }
    }
}
