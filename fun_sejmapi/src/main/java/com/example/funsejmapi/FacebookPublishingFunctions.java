package com.example.funsejmapi;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

/**
 * Publishes a daily Facebook hello post at 06:00 Central European Time.
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

        executionContext.getLogger().info(
                "Publishing daily Facebook hello message. Trigger: " + timerInfo);
        this.facebookPublisher.publish("Hello");
    }
}
