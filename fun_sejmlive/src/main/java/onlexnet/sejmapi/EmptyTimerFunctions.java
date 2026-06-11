package onlexnet.sejmapi;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import onlexnet.infra.adapters.in.schedule.EveryTenMinutesTimerTrigger;

/**
 * Empty timer-triggered function used to measure how quickly the host executes
 * a no-op scheduled invocation.
 */
public final class EmptyTimerFunctions {

    @FunctionName("SejmApiDemo_EmptyTimer")
    public void runEmptyTimer(
            @EveryTenMinutesTimerTrigger
            final String timerInfo,
            final ExecutionContext executionContext) {

        // Intentionally empty to measure the trigger overhead.
    }
}
