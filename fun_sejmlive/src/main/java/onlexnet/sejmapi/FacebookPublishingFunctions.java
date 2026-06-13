package onlexnet.sejmapi;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

/**
 * Publishes daily Sejm digests to social media on a scheduled basis.
 * <p>
 * This component runs daily at 23:30 (CRON: 0 30 23 * * *) and:
 * <ol>
 * <li>Checks for idempotency (skips if digest already published for today)
 * <li>Builds a Polish-language digest from collected Sejm data
 * <li>Publishes to Facebook via FacebookPublisher
 * <li>Logs success or failure to the publish log table
 * </ol>
 * <p>
 * Failure behavior: If publishing fails, the failure is logged. If the failure log write itself
 * fails, the original exception is preserved as the thrown exception and the log failure is
 * attached as a suppressed exception.
 */
@Component
public final class FacebookPublishingFunctions {

    private static final String FUNCTION_NAME = "SejmApiDemo_FacebookPublish";

    private final FacebookPublisher facebookPublisher;
    private final SejmDigestService digestService;
    private final SejmDailyDigestRepository repository;

    public FacebookPublishingFunctions(final FacebookPublisher facebookPublisher,
            final SejmDigestService digestService,
            final SejmDailyDigestRepository repository) {
        this.facebookPublisher = facebookPublisher;
        this.digestService = digestService;
        this.repository = repository;
    }

    /**
     * Publishes today's Sejm digest to Facebook.
     * <p>
     * This is the entry point for the scheduled Azure Function timer trigger.
     * It orchestrates digest building, idempotency checking, publishing, and logging.
     *
     * @param timerInfo schedule trigger information (unused but required by timer binding)
     * @param executionContext Azure Functions execution context for logging
     * @throws Exception if digest service or publisher fails; original exception is rethrown
     *         even if failure-log write fails
     */
    @FunctionName(FUNCTION_NAME)
    public void publishDailyDigest(
            @TimerTrigger(name = "timer", schedule = "0 30 23 * * *")
            final String timerInfo,
            final ExecutionContext executionContext) {
        var date = LocalDate.now();
        if (this.repository.alreadyPublishedToday(date)) {
            executionContext.getLogger().info(
                    "Pomijanie publikacji - wpis dla dnia " + date + " juz istnieje.");
            return;
        }

        try {
            var digest = this.digestService.buildDigest(date);
            if (digest.isEmpty()) {
                executionContext.getLogger().info(
                        "Brak aktywnosci sejmowej dla dnia " + date + ", pomijanie publikacji.");
                return;
            }

            var message = digest.get();
            executionContext.getLogger().info(
                    "Publikowanie podsumowania Sejmu. Trigger: " + timerInfo
                            + ", wiadomosc: " + message);
            this.facebookPublisher.publish(message);
            this.repository.insertPublishLog(date, message, true, null);
        } catch (Exception e) {
            try {
                this.repository.insertPublishLog(date, null, false, e.getMessage());
            } catch (Exception logError) {
                e.addSuppressed(logError);
            }
            throw e;
        }

    }
}
