package onlexnet.app.ports.out;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

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
        @Nullable String lastError) {

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
        recipients = List.copyOf(Objects.requireNonNull(recipients, "recipients must not be null"));
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
        firstQueuedAt = Objects.requireNonNull(firstQueuedAt, "firstQueuedAt must not be null");
    }

    public InterpellationPublishQueueMessage withAttempt(final int nextAttempt) {
        return new InterpellationPublishQueueMessage(
                this.domainMessageId,
                this.termNum,
                this.interpellationNum,
                this.title,
                this.recipients,
                this.sentDate,
                nextAttempt,
                this.firstQueuedAt,
                this.lastError);
    }

    public InterpellationPublishQueueMessage withLastError(final @Nullable String error) {
        return new InterpellationPublishQueueMessage(
                this.domainMessageId,
                this.termNum,
                this.interpellationNum,
                this.title,
                this.recipients,
                this.sentDate,
                this.attempt,
                this.firstQueuedAt,
                error);
    }
}
