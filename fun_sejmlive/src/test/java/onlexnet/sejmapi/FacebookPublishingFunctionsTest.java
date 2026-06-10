package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

class FacebookPublishingFunctionsTest {

    @Test
    void givenFacebookPublishFunction_whenInspectingTimerTrigger_thenRunsEvery5Minutes()
            throws NoSuchMethodException {
        var method = FacebookPublishingFunctions.class.getDeclaredMethod(
                "publishHelloMessage",
                String.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(TimerTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo("SejmApiDemo_FacebookPublish");
        assertThat(trigger).isNotNull();
        assertThat(trigger.schedule()).isEqualTo("0 */5 * * * *");
    }

    @Test
    void givenFacebookPublishFunction_whenInvoked_thenPublishesPolishSummaryMessage() {
        var capturedMessage = new StringBuilder();
        var function = new FacebookPublishingFunctions(message -> capturedMessage.append(message));

        function.publishHelloMessage("timer", new FakeExecutionContext());

        assertThat(capturedMessage.toString())
                .contains("Sejm API")
                .contains("kadencji")
                .contains("aktualna kadencja");
    }

    @Test
    void givenFacebookPublishFunction_whenUsingMockedSejmApiClient_thenPublishesSummaryFromClient() {
        var capturedMessage = new StringBuilder();
        var sejmApiClient = mock(SejmApiClient.class);
        when(sejmApiClient.fetchTerms())
                .thenReturn(List.of(new SejmTerm(true, LocalDate.of(2023, 11, 13), 10,
                        new SejmPrints(2918, null, "/term10/prints"), LocalDate.of(2023, 11, 12))));

        var function = new FacebookPublishingFunctions(message -> capturedMessage.append(message),
                sejmApiClient);

        function.publishHelloMessage("timer", new FakeExecutionContext());

        verify(sejmApiClient).fetchTerms();
        assertThat(capturedMessage.toString())
                .contains("Sejm API: 1 kadencji")
                .contains("aktualna kadencja to 10")
                .contains("2023-11-13")
                .contains("2023-11-12");
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
            return "SejmApiDemo_FacebookPublish";
        }
    }
}
