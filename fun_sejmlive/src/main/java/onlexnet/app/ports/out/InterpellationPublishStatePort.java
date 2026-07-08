package onlexnet.app.ports.out;

import java.time.LocalDate;

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
}
