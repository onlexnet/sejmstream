package onlexnet.app.ports.out;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Queue contract for publishing one INTERPELLATION item to Facebook.
 */
public record InterpellationPublishQueueMessage(
        String domainMessageId,
        int termNum,
        int interpellationNum,
        String title,
        List<String> recipients,
        @Nullable String sentDate,
        int attempt,
        Instant firstQueuedAt,
        @Nullable String lastError,
        @Nullable String webDescription,
        List<AttachmentMetadata> attachments) {

    public InterpellationPublishQueueMessage {
        if (domainMessageId == null || domainMessageId.isBlank()) {
            throw new IllegalArgumentException("domainMessageId must not be blank");
        }
        if (termNum < 1) {
            throw new IllegalArgumentException("termNum must be >= 1");
        }
        if (interpellationNum < 1) {
            throw new IllegalArgumentException("interpellationNum must be >= 1");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        recipients = List.copyOf(recipients);
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public InterpellationPublishQueueMessage(
            String domainMessageId,
            int termNum,
            int interpellationNum,
            String title,
            List<String> recipients,
            @Nullable String sentDate,
            int attempt,
            Instant firstQueuedAt,
            @Nullable String lastError) {
        this(domainMessageId, termNum, interpellationNum, title, recipients, sentDate, attempt, firstQueuedAt, lastError, null, List.of());
    }

    public InterpellationPublishQueueMessage withAttempt(int nextAttempt) {
        return new InterpellationPublishQueueMessage(
                this.domainMessageId,
                this.termNum,
                this.interpellationNum,
                this.title,
                this.recipients,
                this.sentDate,
                nextAttempt,
                this.firstQueuedAt,
                this.lastError,
                this.webDescription,
                this.attachments);
    }

    public InterpellationPublishQueueMessage withLastError(@Nullable String error) {
        return new InterpellationPublishQueueMessage(
                this.domainMessageId,
                this.termNum,
                this.interpellationNum,
                this.title,
                this.recipients,
                this.sentDate,
                this.attempt,
                this.firstQueuedAt,
                error,
                this.webDescription,
                this.attachments);
    }

    public InterpellationPublishQueueMessage withWebDescription(@Nullable String webDescription) {
        return new InterpellationPublishQueueMessage(
                this.domainMessageId,
                this.termNum,
                this.interpellationNum,
                this.title,
                this.recipients,
                this.sentDate,
                this.attempt,
                this.firstQueuedAt,
                this.lastError,
                webDescription,
                this.attachments);
    }

    public InterpellationPublishQueueMessage withAttachments(List<AttachmentMetadata> attachments) {
        return new InterpellationPublishQueueMessage(
                this.domainMessageId,
                this.termNum,
                this.interpellationNum,
                this.title,
                this.recipients,
                this.sentDate,
                this.attempt,
                this.firstQueuedAt,
                this.lastError,
                this.webDescription,
                attachments);
    }
}
