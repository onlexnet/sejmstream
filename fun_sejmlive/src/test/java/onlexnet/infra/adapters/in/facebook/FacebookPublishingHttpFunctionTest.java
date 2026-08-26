package onlexnet.infra.adapters.in.facebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import onlexnet.app.ports.in.publish.PublishDailyDigestOutcome;
import onlexnet.app.ports.in.publish.PublishDailyDigestUseCase;
import onlexnet.infra.adapters.in.facebook.FacebookPublishingFunctionTestSupport.FakeExecutionContext;
import onlexnet.infra.adapters.in.facebook.FacebookPublishingFunctionTestSupport.FakeHttpRequestMessage;

class FacebookPublishingHttpFunctionTest {

    @Test
    void givenFacebookPublishHttpFunction_whenInspectingTrigger_thenPostFunctionAuthAndRouteAreConfigured()
            throws NoSuchMethodException {
        var method = FacebookPublishingHttpFunction.class.getDeclaredMethod(
                "publishDailyDigestHttp",
                HttpRequestMessage.class,
                com.microsoft.azure.functions.ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(HttpTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo("Fun_FacebookPublishStart");
        assertThat(trigger).isNotNull();
        assertThat(trigger.methods()).containsExactly(HttpMethod.POST);
        assertThat(trigger.authLevel()).isEqualTo(AuthorizationLevel.FUNCTION);
        assertThat(trigger.route()).isEqualTo("Fun_FacebookPublishStart");
    }

    @Test
    void givenHttpTriggerAndPublishedOutcome_whenInvoked_thenReturnsPublishedStatus() {
        var publishUseCase = mock(PublishDailyDigestUseCase.class);
        when(publishUseCase.publish(any())).thenReturn(
                new PublishDailyDigestOutcome.Published(LocalDate.now(), "digest msg"));
        var function = new FacebookPublishingHttpFunction(new FacebookPublishingFunctionSupport(publishUseCase));

        var response = function.publishDailyDigestHttp(
                new FakeHttpRequestMessage<>(Optional.empty()),
                new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().toString()).contains("PUBLISHED");
        verify(publishUseCase).publish(any());
    }

    @Test
    void givenHttpTriggerAndAlreadyPublishedOutcome_whenInvoked_thenReturnsSkippedStatus() {
        var publishUseCase = mock(PublishDailyDigestUseCase.class);
        when(publishUseCase.publish(any())).thenReturn(
                new PublishDailyDigestOutcome.SkippedAlreadyPublished(LocalDate.now()));
        var function = new FacebookPublishingHttpFunction(new FacebookPublishingFunctionSupport(publishUseCase));

        var response = function.publishDailyDigestHttp(
                new FakeHttpRequestMessage<>(Optional.empty()),
                new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().toString()).contains("SKIPPED_ALREADY_PUBLISHED");
    }

    @Test
    void givenHttpTriggerAndFailedOutcome_whenInvoked_thenReturnsInternalServerError() {
        var publishUseCase = mock(PublishDailyDigestUseCase.class);
        when(publishUseCase.publish(any())).thenReturn(
                new PublishDailyDigestOutcome.Failed(LocalDate.now(), new IllegalStateException("boom")));
        var function = new FacebookPublishingHttpFunction(new FacebookPublishingFunctionSupport(publishUseCase));

        var response = function.publishDailyDigestHttp(
                new FakeHttpRequestMessage<>(Optional.empty()),
                new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().toString()).contains("Failed to publish daily digest: boom");
    }
}
