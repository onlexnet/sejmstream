package onlexnet.app.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class InterpellationPublishRetryPolicyTest {

    @Test
    void givenDefaultValues_whenPolicyCreated_thenMaxAttemptsDefaultsToFive() {
        var policy = new InterpellationPublishRetryPolicy(5, 60, 2.0, 900);

        assertThat(policy.maxAttempts()).isEqualTo(5);
        assertThat(policy.retryDelayForAttempt(1)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void givenBackoffConfiguration_whenCalculatingRetryDelay_thenAppliesExponentialDelayAndMaxCap() {
        var policy = new InterpellationPublishRetryPolicy(5, 60, 2.0, 300);

        assertThat(policy.retryDelayForAttempt(1)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.retryDelayForAttempt(2)).isEqualTo(Duration.ofSeconds(120));
        assertThat(policy.retryDelayForAttempt(3)).isEqualTo(Duration.ofSeconds(240));
        assertThat(policy.retryDelayForAttempt(4)).isEqualTo(Duration.ofSeconds(300));
    }
}
