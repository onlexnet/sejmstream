package onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;

import lombok.RequiredArgsConstructor;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;

/**
 * Independently observes owned loading instances every minute, including when completion signals are lost.
 */
@Component
@RequiredArgsConstructor
public final class DailyDigestRuntimeObserverFunction {

    private final DailyDigestRuntimeObserverSupport support;

    @FunctionName(SejmCollectFunctions.OBSERVER_FUNCTION_NAME)
    public void observeRuntime(
            @TimerTrigger(name = "timer", schedule = "0 * * * * *") String timerInfo,
            @DurableClientInput(name = "durableContext") DurableClientContext clientContext) {
        support.observe(clientContext.getClient());
    }
}
