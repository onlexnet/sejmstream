package com.example.funsejmapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

class FacebookPublishingFunctionsTest {

    @Test
    void givenFacebookPublishFunction_whenInspectingTimerTrigger_thenRunsDailyAtElevenAmPolishTime()
            throws NoSuchMethodException {
        var method = FacebookPublishingFunctions.class.getDeclaredMethod(
                "publishHelloMessage",
                String.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(TimerTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo("SejmApiDemo_FacebookPublish");
        assertThat(trigger).isNotNull();
        assertThat(trigger.schedule()).isEqualTo("0 0 11 * * *");
    }

    @Test
    void givenFacebookPublishFunction_whenInvoked_thenPublishesHelloMessage() {
        var capturedMessage = new StringBuilder();
        var function = new FacebookPublishingFunctions(message -> capturedMessage.append(message));

        function.publishHelloMessage("timer", new FakeExecutionContext());

        assertThat(capturedMessage.toString()).isEqualTo("Hello");
    }

    private static final class FakeExecutionContext implements ExecutionContext {

        @Override
        public Logger getLogger() {
            return Logger.getLogger(FakeExecutionContext.class.getName());
        }

        @Override
        public String getInvocationId() {
            return "invocation-id";
        }

        @Override
        public String getFunctionName() {
            return "SejmApiDemo_FacebookPublish";
        }
    }
}
