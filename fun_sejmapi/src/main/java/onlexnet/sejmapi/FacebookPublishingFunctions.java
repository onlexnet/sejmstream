package onlexnet.sejmapi;

import java.time.Instant;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

/**
 * Publishes a Facebook hello post every day at 6 AM with the current timestamp.
 */
public final class FacebookPublishingFunctions {

    private static final String FUNCTION_NAME = "SejmApiDemo_FacebookPublish";

    private final FacebookPublisher facebookPublisher;

    public FacebookPublishingFunctions() {
        this(new DefaultFacebookPublisher());
    }

    FacebookPublishingFunctions(final FacebookPublisher facebookPublisher) {
        this.facebookPublisher = facebookPublisher;
    }

    @FunctionName(FUNCTION_NAME)
    public void publishHelloMessage(
            @TimerTrigger(name = "timer", schedule = "0 0 6 * * *")
            final String timerInfo,
            final ExecutionContext executionContext) {

        final String message = "Hello at " + Instant.now();

        executionContext.getLogger().info(
                "Publishing Facebook hello message every day at 6 AM. Trigger: " + timerInfo
                        + ", message: " + message);
        this.facebookPublisher.publish(message);
    }
}
