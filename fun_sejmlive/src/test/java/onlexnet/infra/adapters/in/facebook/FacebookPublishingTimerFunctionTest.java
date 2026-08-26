package onlexnet.infra.adapters.in.facebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import onlexnet.app.ports.in.publish.PublishDailyDigestOutcome;
import onlexnet.app.ports.in.publish.PublishDailyDigestUseCase;
import onlexnet.infra.adapters.in.facebook.FacebookPublishingFunctionTestSupport.FakeExecutionContext;

class FacebookPublishingTimerFunctionTest {

    @Test
    void givenFacebookPublishFunction_whenInspectingTimerTrigger_thenRunsAt1130PmDaily()
            throws NoSuchMethodException {
        var method = FacebookPublishingTimerFunction.class.getDeclaredMethod(
                "publishDailyDigest",
                String.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(TimerTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo("Fun_FacebookPublish");
        assertThat(trigger).isNotNull();
        assertThat(trigger.schedule()).isEqualTo("0 30 23 * * *");
    }

    @Test
    void givenPublishOutcomeFailed_whenTimerInvoked_thenRethrowsOriginalException() {
        var publishUseCase = mock(PublishDailyDigestUseCase.class);
        var publishFailure = new IllegalStateException("publish failed");
        when(publishUseCase.publish(any())).thenReturn(
                new PublishDailyDigestOutcome.Failed(LocalDate.now(), publishFailure));
        var function = new FacebookPublishingTimerFunction(new FacebookPublishingFunctionSupport(publishUseCase));

        assertThatThrownBy(() -> function.publishDailyDigest("timer", new FakeExecutionContext()))
                .isSameAs(publishFailure);
    }
}
