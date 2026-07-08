package onlexnet.app.ports.out;

import java.time.Duration;

/**
 * Output port for queueing INTERPELLATION publish jobs.
 */
public interface InterpellationPublishQueuePort {

    void enqueue(InterpellationPublishQueueMessage message, Duration visibilityDelay);

    void enqueueDeadLetter(InterpellationPublishQueueMessage message);
}
