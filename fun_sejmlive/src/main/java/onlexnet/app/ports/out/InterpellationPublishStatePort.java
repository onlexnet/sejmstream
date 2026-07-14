package onlexnet.app.ports.out;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Output port for tracking per-interpellation publish state.
 */
public interface InterpellationPublishStatePort {

    boolean tryCreateQueuedRecord(InterpellationPublishQueueMessage message, LocalDate collectionDate);

    boolean tryClaimForPublish(InterpellationPublishQueueMessage message);

    boolean isPublished(int termNum, int interpellationNum);

    void markPublished(InterpellationPublishQueueMessage message, String facebookPostMessage);

    void markPublishConfirmationPending(
            InterpellationPublishQueueMessage message,
            String errorMessage,
            String facebookPostMessage);

    void markRetryScheduled(InterpellationPublishQueueMessage message, String errorMessage);

    void markEnqueueFailed(InterpellationPublishQueueMessage message, String errorMessage);

    void markDeadLetter(InterpellationPublishQueueMessage message, String errorMessage);

    /** Returns the reply count last recorded for the given interpellation (0 if not yet tracked). */
    int getLastKnownReplyCount(int termNum, int interpellationNum);

    /** Persists the latest reply count and timestamps the notification if one was published. */
    void updateLastKnownReplyCount(int termNum, int interpellationNum, int replyCount);

    /** Sets reply_notification_published_at to the current timestamp. */
    void markReplyNotificationPublished(int termNum, int interpellationNum, LocalDateTime publishedAt);
}
