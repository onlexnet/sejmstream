package onlexnet.infra.adapters.in.schedule;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Azure Functions timer binding for the demo scheduler.
 *
 * <p>The binding keeps the parameter name fixed as {@code timer} and uses the
 * hard-coded 10-minute cron schedule, which fires every 10 minutes.</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface EveryTenMinutesTimerTrigger {

    String name() default "timer";

    String schedule() default "0 */10 * * * *";
}
