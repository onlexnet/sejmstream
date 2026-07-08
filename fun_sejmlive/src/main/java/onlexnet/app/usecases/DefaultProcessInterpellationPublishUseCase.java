package onlexnet.app.usecases;

import java.time.Duration;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.stereotype.Component;

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
public class DefaultProcessInterpellationPublishUseCase implements ProcessInterpellationPublishUseCase {

    private static final Logger LOGGER = Logger.getLogger(DefaultProcessInterpellationPublishUseCase.class.getName());

    private final FacebookPublisher facebookPublisher;
    private final InterpellationPublishQueuePort queuePort;
    private final InterpellationPublishStatePort publishStatePort;
    private final InterpellationPublishRetryPolicy retryPolicy;

    public DefaultProcessInterpellationPublishUseCase(
            final FacebookPublisher facebookPublisher,
            final InterpellationPublishQueuePort queuePort,
            final InterpellationPublishStatePort publishStatePort,
            final InterpellationPublishRetryPolicy retryPolicy) {
        this.facebookPublisher = Objects.requireNonNull(facebookPublisher, "facebookPublisher must not be null");
        this.queuePort = Objects.requireNonNull(queuePort, "queuePort must not be null");
        this.publishStatePort = Objects.requireNonNull(publishStatePort, "publishStatePort must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
    }

    @Override
    public ProcessInterpellationPublishOutcome process(final ProcessInterpellationPublishCommand command) {
        var message = Objects.requireNonNull(command, "command must not be null").message();
        var claimed = this.publishStatePort.tryClaimForPublish(message);
        if (!claimed) {
            return new ProcessInterpellationPublishOutcome.SkippedAlreadyPublished(
                    message.domainMessageId(),
                    message.termNum(),
                    message.interpellationNum());
        }

        var facebookMessage = this.formatFacebookPost(message);
        try {
            this.facebookPublisher.publish(facebookMessage);
        } catch (RuntimeException exception) {
            return this.handleFailure(message, exception);
        }

        try {
            this.publishStatePort.markPublished(message, facebookMessage);
            return new ProcessInterpellationPublishOutcome.Published(
                    message.domainMessageId(),
                    message.termNum(),
                    message.interpellationNum());
        } catch (RuntimeException exception) {
            return this.handlePostPublishPersistenceFailure(message, facebookMessage, exception);
        }
    }

    private ProcessInterpellationPublishOutcome handlePostPublishPersistenceFailure(
            final InterpellationPublishQueueMessage message,
            final String facebookPostMessage,
            final RuntimeException exception) {
        var error = this.safeErrorMessage(exception);
        try {
            this.publishStatePort.markPublishConfirmationPending(message.withLastError(error), error, facebookPostMessage);
        } catch (RuntimeException markFailure) {
            LOGGER.log(Level.SEVERE,
                    "Published to Facebook but failed to persist publish-confirmation-pending state for "
                            + message.domainMessageId(),
                    markFailure);
        }

        return new ProcessInterpellationPublishOutcome.PublishConfirmationPending(
                message.domainMessageId(),
                message.termNum(),
                message.interpellationNum(),
                error);
    }

    private ProcessInterpellationPublishOutcome handleFailure(
            final InterpellationPublishQueueMessage message,
            final RuntimeException exception) {
        var error = this.safeErrorMessage(exception);
        if (message.attempt() >= this.retryPolicy.maxAttempts()) {
            var deadLetterMessage = message.withLastError(error);
            this.queuePort.enqueueDeadLetter(deadLetterMessage);
            this.publishStatePort.markDeadLetter(deadLetterMessage, error);
            return new ProcessInterpellationPublishOutcome.DeadLettered(
                    message.domainMessageId(),
                    message.termNum(),
                    message.interpellationNum(),
                    message.attempt());
        }

        var retryMessage = message.withAttempt(message.attempt() + 1).withLastError(error);
        Duration retryDelay = this.retryPolicy.retryDelayForAttempt(message.attempt());
        this.queuePort.enqueue(retryMessage, retryDelay);
        this.publishStatePort.markRetryScheduled(retryMessage, error);
        return new ProcessInterpellationPublishOutcome.RetryScheduled(
                message.domainMessageId(),
                message.termNum(),
                message.interpellationNum(),
                retryMessage.attempt());
    }

    private String formatFacebookPost(final InterpellationPublishQueueMessage message) {
        var recipients = message.recipients() == null || message.recipients().isEmpty()
                ? "brak"
                : String.join(", ", message.recipients());

        var builder = new StringJoiner("\n");
        builder.add("Interpelacja nr " + message.interpellationNum() + " (kadencja " + message.termNum() + ")");
        builder.add(message.title());
        builder.add("Adresaci: " + recipients);
        if (message.sentDate() != null && !message.sentDate().isBlank()) {
            builder.add("Data zlozenia: " + message.sentDate());
        }
        return builder.toString();
    }

    private String safeErrorMessage(final RuntimeException exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
