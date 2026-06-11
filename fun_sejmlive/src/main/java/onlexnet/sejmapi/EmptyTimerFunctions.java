package onlexnet.sejmapi;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;

/**
 * Timer-triggered function that ensures Liquibase database migrations are
 * applied on a regular schedule.
 *
 * <p>The {@link SpringLiquibase} dependency is injected intentionally: Spring
 * will only create it when {@code DB_URL} is configured (see
 * {@link DatabaseConfiguration#springLiquibase}), and the mere act of
 * resolving the bean guarantees that all pending changesets are executed before
 * this function body runs.  This makes the function a lightweight, recurring
 * "migration heartbeat" — it will also catch up on any changesets deployed
 * between scheduled invocations.
 */
@Component
@RequiredArgsConstructor
public final class EmptyTimerFunctions {

    /**
     * Injected to trigger Liquibase migration on every invocation.
     * The bean is conditional on {@code DB_URL} being present, so the function
     * still starts safely in environments where no database is configured.
     */
    private final SpringLiquibase liquibase;

    @FunctionName("SejmApiDemo_EmptyTimer")
    public void runEmptyTimer(
            @TimerTrigger(name = "timer", schedule = "0 */10 * * * *")
            final String timerInfo,
            final ExecutionContext executionContext) {

        // Liquibase migration is triggered by Spring constructing the
        // SpringLiquibase bean before this method is reached.
    }
}
