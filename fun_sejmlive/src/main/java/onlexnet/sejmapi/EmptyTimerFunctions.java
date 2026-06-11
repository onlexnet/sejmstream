package onlexnet.sejmapi;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;

/**
 * Empty timer-triggered function used to measure how quickly the host executes
 * a no-op scheduled invocation.
 */
@Component
@RequiredArgsConstructor
public final class EmptyTimerFunctions {

    private final SpringLiquibase liquibase;

    @FunctionName("SejmApiDemo_EmptyTimer")
    public void runEmptyTimer(
            @TimerTrigger(name = "timer", schedule = "0 */10 * * * *")
            final String timerInfo,
            final ExecutionContext executionContext) {

        // Intentionally empty to measure the trigger overhead.
    }
}
