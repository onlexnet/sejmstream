package onlexnet.infra.adapters.in.facebook;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import lombok.RequiredArgsConstructor;

/**
 * Timer trigger that publishes daily Sejm digests to Facebook on a scheduled basis.
 */
@Component
@RequiredArgsConstructor
public final class FacebookPublishingTimerFunction {

    private final FacebookPublishingFunctionSupport support;

    @FunctionName(FacebookPublishingFunctions.TIMER_FUNCTION_NAME)
    public void publishDailyDigest(
            @TimerTrigger(name = "timer", schedule = "0 30 23 * * *")
            String timerInfo,
            ExecutionContext execCtx) {
        this.support.publishDailyDigest(timerInfo, execCtx);
    }
}
