package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

class EmptyTimerFunctionsTest {

    @Test
    void givenEmptyTimerFunction_whenInspectingTimerTrigger_thenRunsEvery10Minutes()
            throws NoSuchMethodException {
        var method = EmptyTimerFunctions.class.getDeclaredMethod(
                "runEmptyTimer",
                String.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(TimerTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo("SejmApiDemo_EmptyTimer");
        assertThat(trigger).isNotNull();
        assertThat(trigger.schedule()).isEqualTo("0 */10 * * * *");
    }

    @Test
    void givenEmptyTimerFunction_whenInvoked_thenDoesNotThrow() {
        var function = new EmptyTimerFunctions();

        assertThatCode(() -> function.runEmptyTimer("timer", new FakeExecutionContext()))
                .doesNotThrowAnyException();
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
            return "SejmApiDemo_EmptyTimer";
        }
    }
}
