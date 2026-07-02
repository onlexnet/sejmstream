package onlexnet.app.ports.in.collect;

/**
 * Application use case for collecting daily Sejm activity.
 */
public interface CollectDailyDigestUseCase {

    /**
     * Executes one daily collect attempt for a specific date.
     */
    CollectDailyDigestOutcome collect(CollectDailyDigestCommand command);
}
