package onlexnet.app.ports.in.publish;

/**
 * Application use case for publishing daily Sejm digest content.
 */
public interface PublishDailyDigestUseCase {

    /**
     * Executes one daily publish attempt for a specific date.
     */
    PublishDailyDigestOutcome publish(PublishDailyDigestCommand command);
}