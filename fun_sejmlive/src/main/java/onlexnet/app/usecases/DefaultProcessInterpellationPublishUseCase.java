package onlexnet.app.usecases;

import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishCommand;
import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishOutcome;
import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishUseCase;
import onlexnet.app.ports.out.AttachmentMetadata;
import onlexnet.app.ports.out.FacebookPublisher;
import onlexnet.app.ports.out.InterpellationPublishQueueMessage;
import onlexnet.app.ports.out.InterpellationPublishQueuePort;
import onlexnet.app.ports.out.InterpellationPublishStatePort;
import onlexnet.app.ports.out.ProjectOwnerNotifier;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.shared.Guards;

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
    private final SejmApiClient sejmApiClient;
    private final ProjectOwnerNotifier projectOwnerNotifier;


    @Override
    public ProcessInterpellationPublishOutcome process(ProcessInterpellationPublishCommand command) {
        var message = this.messageFrom(command);
        if (!this.publishStatePort.tryClaimForPublish(message)) {
            return this.skippedAlreadyPublishedOutcome(message);
        }

        var attachmentSummary = this.firstAttachmentSummary(message);
        var facebookMessage = this.formatFacebookPost(message, attachmentSummary);
        try {
            var postId = this.facebookPublisher.publish(facebookMessage);
            this.publishDescriptionCommentIfPresent(postId, message.webDescription());
            this.publishAttachmentCommentIfPresent(postId, attachmentSummary);
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
        return command.message();
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

    private String formatFacebookPost(InterpellationPublishQueueMessage message, @Nullable String attachmentSummary) {
        var builder = new StringJoiner("\n");
        builder.add("Interpelacja nr " + message.interpellationNum() + " (kadencja " + message.termNum() + ")");
        builder.add(message.title());
        builder.add("Adresaci: " + this.formatRecipients(message));
        this.appendSentDateIfPresent(builder, message);
        this.appendAttachmentSummaryIfPresent(builder, attachmentSummary);
        return builder.toString();
    }

    private String formatRecipients(InterpellationPublishQueueMessage message) {
        var recipients = message.recipients();
        var safeRecipients = Guards.orDefaultIfNullOrEmpty(
                recipients,
                java.util.Collections.<String>emptyList());
        if (safeRecipients.isEmpty()) {
            return "brak";
        }
        return String.join(", ", safeRecipients);
    }

    private void appendSentDateIfPresent(StringJoiner builder, InterpellationPublishQueueMessage message) {
        if (message.sentDate() != null && !message.sentDate().isBlank()) {
            builder.add("Data zlozenia: " + message.sentDate());
        }
    }

    private void appendAttachmentSummaryIfPresent(StringJoiner builder, @Nullable String attachmentSummary) {
        if (attachmentSummary != null && !attachmentSummary.isBlank()) {
            builder.add("Skrót załącznika: " + attachmentSummary);
        }
    }

    private void publishDescriptionCommentIfPresent(@Nullable String postId, @Nullable String webDescription) {
        if (postId == null || postId.isBlank()) {
            return;
        }
        if (webDescription == null || webDescription.isBlank()) {
            return;
        }
        this.facebookPublisher.publishComment(postId, "Opis: " + webDescription);
    }

    private void publishAttachmentCommentIfPresent(@Nullable String postId, @Nullable String attachmentSummary) {
        if (postId == null || postId.isBlank()) {
            return;
        }
        if (attachmentSummary == null || attachmentSummary.isBlank()) {
            return;
        }
        this.facebookPublisher.publishComment(postId, "Załącznik: " + attachmentSummary);
    }

    private @Nullable String firstAttachmentSummary(InterpellationPublishQueueMessage message) {
        var attachments = Guards.orDefaultIfNullOrEmpty(
                message.attachments(),
                java.util.Collections.<AttachmentMetadata>emptyList());
        if (this.sejmApiClient == null || attachments.isEmpty()) {
            return null;
        }
        for (var attachment : attachments) {
            var summary = this.fetchAttachmentSummary(message, attachment);
            if (summary != null && !summary.isBlank()) {
                return summary;
            }
        }
        return null;
    }

    private @Nullable String fetchAttachmentSummary(
            InterpellationPublishQueueMessage message,
            @Nullable AttachmentMetadata attachment) {
        if (attachment == null) {
            return null;
        }
        var fileName = attachment.fileName();
        if (fileName == null || fileName.isBlank()) {
            fileName = attachment.name();
        }
        if (fileName == null || fileName.isBlank() || attachment.replyKey() == null || attachment.replyKey().isBlank()) {
            return null;
        }
        var client = this.sejmApiClient;
        if (client == null) {
            return null;
        }
        try {
            var fetched = client.fetchAttachmentText(message.termNum(), attachment.replyKey(), fileName);
            return switch (fetched) {
                case SejmApiClient.AttachmentFetchResult.PdfText pdfText -> this.summarizeAttachmentText(pdfText.text());
                case SejmApiClient.AttachmentFetchResult.Unsupported unsupported -> {
                    this.notifyOwnerAboutUnsupportedAttachmentCase(message, unsupported);
                    yield null;
                }
                case SejmApiClient.AttachmentFetchResult.Unavailable _ -> null;
            };
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to fetch attachment summary for replyKey=" + attachment.replyKey(), exception);
            return null;
        }
    }

    private void notifyOwnerAboutUnsupportedAttachmentCase(
            InterpellationPublishQueueMessage message,
            SejmApiClient.AttachmentFetchResult.Unsupported unsupported) {
        var notifier = this.projectOwnerNotifier;
        if (notifier == null) {
            return;
        }

        var alertMessage = "Nowy nieobslugiwany zalacznik interpelacji:"
                + "\nTerm: " + message.termNum()
                + "\nInterpelacja: " + message.interpellationNum()
                + "\nReplyKey: " + unsupported.replyKey()
                + "\nPlik: " + unsupported.fileName()
                + "\nMimeType: " + unsupported.mimeType()
                + "\nRozmiar: " + unsupported.sizeBytes() + " B"
                + "\nPowod: " + unsupported.reason();

        try {
            notifier.notifyOwner(alertMessage);
        } catch (RuntimeException notificationFailure) {
            LOGGER.log(Level.WARNING, "Failed to notify project owner about unsupported attachment case", notificationFailure);
        }
    }

    private @Nullable String summarizeAttachmentText(@Nullable String text) {
        if (text == null) {
            return null;
        }
        var normalized = text.strip();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() <= 200) {
            return normalized;
        }
        return normalized.substring(0, 197).trim() + "...";
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
