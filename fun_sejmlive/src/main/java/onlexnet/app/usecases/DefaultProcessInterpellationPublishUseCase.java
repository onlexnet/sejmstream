package onlexnet.app.usecases;

import java.util.Objects;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishCommand;
import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishOutcome;
import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishUseCase;
import onlexnet.app.ports.out.FacebookPublisher;
import onlexnet.app.ports.out.InterpellationPublishQueuePort;
import onlexnet.app.ports.out.InterpellationPublishQueueMessage;
import onlexnet.app.ports.out.InterpellationPublishStatePort;

/**
 * App-layer processor for queue-driven INTERPELLATION publishing to Facebook.
 */
@Component
@RequiredArgsConstructor
public class DefaultProcessInterpellationPublishUseCase implements ProcessInterpellationPublishUseCase {

    private static final Logger LOGGER = Logger.getLogger(DefaultProcessInterpellationPublishUseCase.class.getName());

    private final FacebookPublisher facebookPublisher;
    private final InterpellationPublishQueuePort queuePort;
    private final InterpellationPublishStatePort publishStatePort;
    private final InterpellationPublishRetryPolicy retryPolicy;

    @Override
    public ProcessInterpellationPublishOutcome process(ProcessInterpellationPublishCommand command) {
        var message = this.messageFrom(command);
        if (!this.publishStatePort.tryClaimForPublish(message)) {
            return this.skippedAlreadyPublishedOutcome(message);
        }

        var facebookMessage = this.formatFacebookPost(message);
        try {
            this.facebookPublisher.publish(facebookMessage);
        } catch (RuntimeException exception) {
            return this.handlePublishFailure(message, exception);
        }

        try {
            this.publishStatePort.markPublished(message, facebookMessage);
            return this.publishedOutcome(message);
        } catch (RuntimeException exception) {
            return this.handlePostPublishPersistenceFailure(message, facebookMessage, exception);
        }
    }

    private InterpellationPublishQueueMessage messageFrom(ProcessInterpellationPublishCommand command) {
        var nonNullCommand = Objects.requireNonNull(command, "command must not be null");
        return Objects.requireNonNull(nonNullCommand.message(), "command.message must not be null");
    }

    private ProcessInterpellationPublishOutcome handlePostPublishPersistenceFailure(
            InterpellationPublishQueueMessage message,
            String facebookPostMessage,
            RuntimeException exception) {
        var error = this.safeErrorMessage(exception);
        this.markPublishConfirmationPendingBestEffort(message, facebookPostMessage, error);
        return this.publishConfirmationPendingOutcome(message, error);
    }

    private void markPublishConfirmationPendingBestEffort(
            InterpellationPublishQueueMessage message,
            String facebookPostMessage,
            String error) {
        var pendingMessage = message.withLastError(error);
        try {
            this.publishStatePort.markPublishConfirmationPending(pendingMessage, error, facebookPostMessage);
        } catch (RuntimeException markFailure) {
            LOGGER.log(Level.SEVERE,
                    "Published to Facebook but failed to persist publish-confirmation-pending state for "
                            + message.domainMessageId(),
                    markFailure);
        }
    }

    private ProcessInterpellationPublishOutcome handlePublishFailure(
            InterpellationPublishQueueMessage message,
            RuntimeException exception) {
        var error = this.safeErrorMessage(exception);
        if (this.hasReachedMaxAttempts(message)) {
            var deadLetterMessage = message.withLastError(error);
            this.queuePort.enqueueDeadLetter(deadLetterMessage);
            this.publishStatePort.markDeadLetter(deadLetterMessage, error);
            return this.deadLetteredOutcome(message);
        }

        var retryMessage = message.withAttempt(message.attempt() + 1).withLastError(error);
        var retryDelay = this.retryPolicy.retryDelayForAttempt(message.attempt());
        this.queuePort.enqueue(retryMessage, retryDelay);
        this.publishStatePort.markRetryScheduled(retryMessage, error);
        return this.retryScheduledOutcome(message, retryMessage);
    }

    private boolean hasReachedMaxAttempts(InterpellationPublishQueueMessage message) {
        return message.attempt() >= this.retryPolicy.maxAttempts();
    }

    private String formatFacebookPost(InterpellationPublishQueueMessage message) {
        var builder = new StringJoiner("\n");
        builder.add("Interpelacja nr " + message.interpellationNum() + " (kadencja " + message.termNum() + ")");
        builder.add(message.title());
        builder.add("Adresaci: " + this.formatRecipients(message));
        this.appendSentDateIfPresent(builder, message);
        return builder.toString();
    }

    private String formatRecipients(InterpellationPublishQueueMessage message) {
        var recipients = message.recipients();
        if (recipients == null || recipients.isEmpty()) {
            return "brak";
        }
        return String.join(", ", recipients);
    }

    private void appendSentDateIfPresent(StringJoiner builder, InterpellationPublishQueueMessage message) {
        if (message.sentDate() != null && !message.sentDate().isBlank()) {
            builder.add("Data zlozenia: " + message.sentDate());
        }
    }

    private ProcessInterpellationPublishOutcome.SkippedAlreadyPublished skippedAlreadyPublishedOutcome(
            InterpellationPublishQueueMessage message) {
        return new ProcessInterpellationPublishOutcome.SkippedAlreadyPublished(
                message.domainMessageId(),
                message.termNum(),
                message.interpellationNum());
    }

    private ProcessInterpellationPublishOutcome.Published publishedOutcome(
            InterpellationPublishQueueMessage message) {
        return new ProcessInterpellationPublishOutcome.Published(
                message.domainMessageId(),
                message.termNum(),
                message.interpellationNum());
    }

    private ProcessInterpellationPublishOutcome.DeadLettered deadLetteredOutcome(
            InterpellationPublishQueueMessage message) {
        return new ProcessInterpellationPublishOutcome.DeadLettered(
                message.domainMessageId(),
                message.termNum(),
                message.interpellationNum(),
                message.attempt());
    }

    private ProcessInterpellationPublishOutcome.RetryScheduled retryScheduledOutcome(
            InterpellationPublishQueueMessage originalMessage,
            InterpellationPublishQueueMessage retryMessage) {
        return new ProcessInterpellationPublishOutcome.RetryScheduled(
                originalMessage.domainMessageId(),
                originalMessage.termNum(),
                originalMessage.interpellationNum(),
                retryMessage.attempt());
    }

    private ProcessInterpellationPublishOutcome.PublishConfirmationPending publishConfirmationPendingOutcome(
            InterpellationPublishQueueMessage message,
            String error) {
        return new ProcessInterpellationPublishOutcome.PublishConfirmationPending(
                message.domainMessageId(),
                message.termNum(),
                message.interpellationNum(),
                error);
    }

    private String safeErrorMessage(RuntimeException exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
