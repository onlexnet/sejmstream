package onlexnet.infra.adapters.in.azurefunc;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public final class SejmCollectTimerFunction {

    private final SejmCollectFunctionSupport support;

    @FunctionName(SejmCollectFunctions.TIMER_FUNCTION_NAME)
    public void runTimer(
            @TimerTrigger(name = "timer", schedule = "0 0 * * * *") final String timerInfo,
            @DurableClientInput(name = "durableContext") final DurableClientContext clientCtx,
            final ExecutionContext execCtx) {
        this.support.runTimer(timerInfo, clientCtx, execCtx);
    }
}
