package onlexnet.app.usecases;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configurable retry policy for INTERPELLATION queue processing.
 */
@Component
public class InterpellationPublishRetryPolicy {

    private static final int MIN_ATTEMPTS = 1;

    private final int maxAttempts;
    private final Duration retryDelay;
    private final double backoffMultiplier;
    private final Duration maxRetryDelay;

    public InterpellationPublishRetryPolicy(
            @Value("${interpellation.publish.queue.max-attempts:5}") final int maxAttempts,
            @Value("${interpellation.publish.queue.retry-delay-seconds:60}")
            final long retryDelaySeconds,
            @Value("${interpellation.publish.queue.backoff-multiplier:2.0}")
            final double backoffMultiplier,
            @Value("${interpellation.publish.queue.max-retry-delay-seconds:900}")
            final long maxRetryDelaySeconds) {
        this.maxAttempts = Math.max(MIN_ATTEMPTS, maxAttempts);
        this.retryDelay = Duration.ofSeconds(Math.max(1L, retryDelaySeconds));
        this.backoffMultiplier = backoffMultiplier < 1.0 ? 1.0 : backoffMultiplier;
        this.maxRetryDelay = Duration.ofSeconds(Math.max(this.retryDelay.toSeconds(), maxRetryDelaySeconds));
    }

    public int maxAttempts() {
        return this.maxAttempts;
    }

    public Duration retryDelayForAttempt(final int currentAttempt) {
        var exponent = Math.max(0, currentAttempt - 1);
        var seconds = (long) Math.min(
                this.maxRetryDelay.toSeconds(),
                Math.round(this.retryDelay.toSeconds() * Math.pow(this.backoffMultiplier, exponent)));
        return Duration.ofSeconds(Math.max(1L, seconds));
    }
}
