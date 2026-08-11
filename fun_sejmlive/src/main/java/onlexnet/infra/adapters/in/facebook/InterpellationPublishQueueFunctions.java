package onlexnet.infra.adapters.in.facebook;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.CRC32;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;

import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishCommand;
import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishOutcome;
import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishUseCase;
import onlexnet.app.ports.out.InterpellationPublishQueueMessage;
import onlexnet.app.ports.out.InterpellationPublishQueuePort;
import onlexnet.app.ports.out.InterpellationPublishStatePort;
import onlexnet.infra.adapters.in.azurefunc.Logger;

/**
 * Queue-triggered adapter that processes one INTERPELLATION publish message at a time.
 */
@Component
public class InterpellationPublishQueueFunctions {

    static final String QUEUE_TRIGGER_FUNCTION_NAME = "Fun_InterpellationPublishFromQueue";

    private final ProcessInterpellationPublishUseCase useCase;
    private final ObjectMapper objectMapper;
    private final InterpellationPublishQueuePort queuePort;
    private final InterpellationPublishStatePort publishStatePort;

    public InterpellationPublishQueueFunctions(
            final ProcessInterpellationPublishUseCase useCase,
            final ObjectMapper objectMapper,
            final InterpellationPublishQueuePort queuePort,
            final InterpellationPublishStatePort publishStatePort) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
        this.queuePort = queuePort;
        this.publishStatePort = publishStatePort;
    }

    @FunctionName(QUEUE_TRIGGER_FUNCTION_NAME)
    public void process(
            @QueueTrigger(
                    name = "message",
                    queueName = "%INTERPELLATION_PUBLISH_QUEUE_NAME%",
                // Points to the domain-logic storage account ("DomainStorage" app setting), which is
                // separate from "AzureWebJobsStorage" (reserved for the Functions host/runtime).
                connection = "DomainStorage")
            // Consumer expects a raw JSON string produced by QueueClient.sendMessage(String).
            // This requires host.json queues.messageEncoding="none"; base64 mode would fail before this method is invoked.
            final String queueMessage,
            ExecutionContext execCtx) {
        final InterpellationPublishQueueMessage payload;
        try {
            payload = this.deserialize(queueMessage);
        } catch (IllegalArgumentException exception) {
            this.handleMalformedMessage(queueMessage, exception, execCtx);
            return;
        }

        var outcome = this.useCase.process(new ProcessInterpellationPublishCommand(payload));
        this.logOutcome(outcome, execCtx);
    }

    private InterpellationPublishQueueMessage deserialize(final String queueMessage) {
        try {
            return this.objectMapper.readValue(queueMessage, InterpellationPublishQueueMessage.class);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid interpellation publish queue message",
                    exception);
        }
    }

    private void handleMalformedMessage(
            final String rawPayload,
            final RuntimeException exception,
            ExecutionContext execCtx) {
        var errorMessage = this.safeErrorMessage(exception);
        var malformedMessage = this.buildMalformedDeadLetterMessage(rawPayload, errorMessage);
        this.queuePort.enqueueDeadLetter(malformedMessage);
        this.publishStatePort.markDeadLetter(malformedMessage, errorMessage);
        execCtx.getLogger().severe(
                "Dead-lettered malformed interpellation queue payload, domainMessageId="
                        + malformedMessage.domainMessageId());
    }

    private InterpellationPublishQueueMessage buildMalformedDeadLetterMessage(
            final String rawPayload,
            final String errorMessage) {
        var payloadChecksum = this.crc32(rawPayload);
        var termNum = 900_000_000 + (int) ((payloadChecksum >>> 16) & 0x7FFF);
        var interpellationNum = 900_000_000 + (int) (payloadChecksum & 0x7FFF);
        var truncatedPayload = rawPayload.length() > 400
                ? rawPayload.substring(0, 400)
                : rawPayload;

        return new InterpellationPublishQueueMessage(
                "malformed-" + Long.toUnsignedString(payloadChecksum),
                termNum,
                interpellationNum,
                "Malformed interpellation queue payload",
                List.of(),
                null,
                1,
                Instant.now(),
                "MALFORMED: " + errorMessage + " payload=" + truncatedPayload);
    }

    private long crc32(final String payload) {
        var crc32 = new CRC32();
        crc32.update(payload.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }

    private String safeErrorMessage(final RuntimeException exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private void logOutcome(
            final ProcessInterpellationPublishOutcome outcome,
            ExecutionContext execCtx) {
        switch (outcome) {
            case ProcessInterpellationPublishOutcome.Published published ->
                Logger.info(execCtx,
                        "Published interpellation " + published.termNum() + "/" + published.interpellationNum());
                case ProcessInterpellationPublishOutcome.PublishConfirmationPending pending ->
                execCtx.getLogger().severe(
                    "Published to Facebook but failed to persist confirmation for interpellation "
                        + pending.termNum() + "/" + pending.interpellationNum()
                        + ", error=" + pending.errorMessage());
            case ProcessInterpellationPublishOutcome.RetryScheduled retry ->
                execCtx.getLogger().warning(
                        "Retry scheduled for interpellation " + retry.termNum() + "/"
                                + retry.interpellationNum() + ", nextAttempt=" + retry.nextAttempt());
            case ProcessInterpellationPublishOutcome.DeadLettered deadLettered ->
                execCtx.getLogger().severe(
                        "Dead-lettered interpellation " + deadLettered.termNum() + "/"
                                + deadLettered.interpellationNum() + " after attempts=" + deadLettered.attemptsUsed());
            case ProcessInterpellationPublishOutcome.SkippedAlreadyPublished skipped ->
                Logger.info(execCtx,
                        "Skipped already published interpellation " + skipped.termNum() + "/"
                                + skipped.interpellationNum());
        }
    }

}
