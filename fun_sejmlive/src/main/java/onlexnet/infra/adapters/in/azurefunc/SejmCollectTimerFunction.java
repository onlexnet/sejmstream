package onlexnet.infra.adapters.in.azurefunc;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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

    private static final ZoneId COLLECTION_ZONE = ZoneId.of("Europe/Warsaw");
    private static final int COLLECTION_HOUR = 1;

    private final SejmCollectFunctionSupport support;
    private final Clock clock;

    @FunctionName(SejmCollectFunctions.TIMER_FUNCTION_NAME)
    public void runTimer(
            @TimerTrigger(name = "timer", schedule = "0 0 0,23 * * *") String timerInfo,
            @DurableClientInput(name = "durableContext") DurableClientContext clientCtx,
            ExecutionContext execCtx) {
        // Linux Flex Consumption timers use UTC; select the candidate matching Warsaw winter/summer time.
        if (ZonedDateTime.now(clock).withZoneSameInstant(COLLECTION_ZONE).getHour() != COLLECTION_HOUR) {
            Log.info(execCtx, "Skipping collect timer outside 01:00 Europe/Warsaw");
            return;
        }
        this.support.runTimer(timerInfo, clientCtx, execCtx);
    }
}
