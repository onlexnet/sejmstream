package onlexnet.sejmapi;

import java.time.Instant;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

/**
 * Publishes a Facebook hello post every 10 minutes with the current timestamp.
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
            @TimerTrigger(name = "timer", schedule = "0 */10 * * * *")
            final String timerInfo,
            final ExecutionContext executionContext) {

        final String message = "Hello at " + Instant.now();

        executionContext.getLogger().info(
                "Publishing Facebook hello message every 10 minutes. Trigger: " + timerInfo
                        + ", message: " + message);
        this.facebookPublisher.publish(message);
    }
}
