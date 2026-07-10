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

    public AzureStorageInterpellationPublishQueue(
            // "Storage" is dedicated to domain-logic storage (queues), kept separate from
            // "AzureWebJobsStorage" which is reserved for the Azure Functions host/runtime.
            @Value("${Storage:}") final String storageConnectionString,
            @Value("${interpellation.publish.queue.name:sejm-interpellations-publish}")
            final String publishQueueName,
            @Value("${interpellation.publish.queue.dead-letter-name:sejm-interpellations-publish-deadletter}")
            final String deadLetterQueueName,
            final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

            if (storageConnectionString == null || storageConnectionString.isBlank()) {
                this.publishQueueClient = null;
                this.deadLetterQueueClient = null;
                return;
            }

            this.publishQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(publishQueueName)
                .buildClient();
            this.deadLetterQueueClient = new QueueClientBuilder()
                .connectionString(storageConnectionString)
                .queueName(deadLetterQueueName)
                .buildClient();
            this.publishQueueClient.createIfNotExists();
            this.deadLetterQueueClient.createIfNotExists();
    }

    @Override
    public void enqueue(final InterpellationPublishQueueMessage message, final Duration visibilityDelay) {
        var queueClient = this.requirePublishQueueClient();
        // Producer sends raw JSON text. The Functions host queue trigger must use host.json queues.messageEncoding="none".
        var payload = this.serialize(message);
        if (visibilityDelay == null || visibilityDelay.isZero() || visibilityDelay.isNegative()) {
            queueClient.sendMessage(payload);
            return;
        }
        queueClient.sendMessageWithResponse(payload, visibilityDelay, null, null, Context.NONE);
    }

    @Override
    public void enqueueDeadLetter(final InterpellationPublishQueueMessage message) {
        var queueClient = this.requireDeadLetterQueueClient();
        // Keep dead-letter payload in the same raw JSON format as the primary queue.
        var payload = this.serialize(message);
        queueClient.sendMessage(payload);
    }

    private QueueClient requirePublishQueueClient() {
        if (this.publishQueueClient == null) {
            throw new IllegalStateException("Storage connection string is required for queue operations");
        }
        return this.publishQueueClient;
    }

    private QueueClient requireDeadLetterQueueClient() {
        if (this.deadLetterQueueClient == null) {
            throw new IllegalStateException("Storage connection string is required for queue operations");
        }
        return this.deadLetterQueueClient;
    }

    private String serialize(final InterpellationPublishQueueMessage message) {
        try {
            return this.objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize queue message", exception);
        }
    }
}
