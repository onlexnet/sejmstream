package onlexnet.app.usecases;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import onlexnet.app.ports.in.publish.PublishDailyDigestCommand;
import onlexnet.app.ports.in.publish.PublishDailyDigestOutcome;
import onlexnet.app.ports.in.publish.PublishDailyDigestUseCase;
import onlexnet.app.ports.out.FacebookPublisher;
import onlexnet.app.ports.out.SejmDailyDigestPersistence;

/**
 * Default app-layer orchestration for one daily Facebook digest publish attempt.
 */
@Component
@RequiredArgsConstructor
public class DefaultPublishDailyDigestUseCase implements PublishDailyDigestUseCase {

    private final FacebookPublisher facebookPublisher;
    private final SejmDigestService digestService;
    private final SejmDailyDigestPersistence repository;

    @Override
    public PublishDailyDigestOutcome publish(PublishDailyDigestCommand command) {
        var date = command.date();
        if (this.repository.alreadyPublishedToday(date)) {
            return new PublishDailyDigestOutcome.SkippedAlreadyPublished(date);
        }

        try {
            var digest = this.digestService.buildDigest(date);
            if (digest.isEmpty()) {
                return new PublishDailyDigestOutcome.SkippedNoDigest(date);
            }

            var message = digest.get();
            this.facebookPublisher.publish(message);
            this.repository.insertPublishLog(date, message, true, null);
            return new PublishDailyDigestOutcome.Published(date, message);
        } catch (RuntimeException exception) {
            try {
                this.repository.insertPublishLog(date, null, false, exception.getMessage());
            } catch (RuntimeException logError) {
                exception.addSuppressed(logError);
            }
            return new PublishDailyDigestOutcome.Failed(date, exception);
        }
    }
}