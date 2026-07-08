package onlexnet.app.ports.in.interpellation;

/**
 * Result of processing one INTERPELLATION publish queue message.
 */
public sealed interface ProcessInterpellationPublishOutcome
        permits ProcessInterpellationPublishOutcome.Published,
        ProcessInterpellationPublishOutcome.PublishConfirmationPending,
        ProcessInterpellationPublishOutcome.RetryScheduled,
        ProcessInterpellationPublishOutcome.DeadLettered,
        ProcessInterpellationPublishOutcome.SkippedAlreadyPublished {

    record Published(String domainMessageId, int termNum, int interpellationNum)
            implements ProcessInterpellationPublishOutcome {
    }

    record PublishConfirmationPending(String domainMessageId, int termNum, int interpellationNum, String errorMessage)
            implements ProcessInterpellationPublishOutcome {
    }

    record RetryScheduled(String domainMessageId, int termNum, int interpellationNum, int nextAttempt)
            implements ProcessInterpellationPublishOutcome {
    }

    record DeadLettered(String domainMessageId, int termNum, int interpellationNum, int attemptsUsed)
            implements ProcessInterpellationPublishOutcome {
    }

    record SkippedAlreadyPublished(String domainMessageId, int termNum, int interpellationNum)
            implements ProcessInterpellationPublishOutcome {
    }
}
