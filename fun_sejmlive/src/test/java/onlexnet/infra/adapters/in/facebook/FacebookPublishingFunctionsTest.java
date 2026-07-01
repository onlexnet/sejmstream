package onlexnet.infra.adapters.in.facebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import onlexnet.app.ports.in.publish.PublishDailyDigestOutcome;
import onlexnet.app.ports.in.publish.PublishDailyDigestUseCase;

class FacebookPublishingFunctionsTest {

    @Test
    void givenFacebookPublishHttpFunction_whenInspectingTrigger_thenPostFunctionAuthAndRouteAreConfigured()
            throws NoSuchMethodException {
        var method = FacebookPublishingFunctions.class.getDeclaredMethod(
                "publishDailyDigestHttp",
                HttpRequestMessage.class,
                ExecutionContext.class);

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
    void givenFacebookPublishFunction_whenInspectingTimerTrigger_thenRunsAt1130PmDaily()
            throws NoSuchMethodException {
        var method = FacebookPublishingFunctions.class.getDeclaredMethod(
                "publishDailyDigest",
                String.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(TimerTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo("Fun_FacebookPublish");
        assertThat(trigger).isNotNull();
        assertThat(trigger.schedule()).isEqualTo("0 30 23 * * *");
    }

    @Test
    void givenPublishOutcomeFailed_whenTimerInvoked_thenRethrowsOriginalException() {
        var publishUseCase = mock(PublishDailyDigestUseCase.class);
        var publishFailure = new IllegalStateException("publish failed");
        when(publishUseCase.publish(any())).thenReturn(
                new PublishDailyDigestOutcome.Failed(LocalDate.now(), publishFailure));
        var function = new FacebookPublishingFunctions(publishUseCase);

        assertThatThrownBy(() -> function.publishDailyDigest("timer", new FakeExecutionContext()))
                .isSameAs(publishFailure);
    }

    @Test
    void givenHttpTriggerAndPublishedOutcome_whenInvoked_thenReturnsPublishedStatus() {
        var publishUseCase = mock(PublishDailyDigestUseCase.class);
        when(publishUseCase.publish(any())).thenReturn(
                new PublishDailyDigestOutcome.Published(LocalDate.now(), "digest msg"));
        var function = new FacebookPublishingFunctions(publishUseCase);

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
        var function = new FacebookPublishingFunctions(publishUseCase);

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
        var function = new FacebookPublishingFunctions(publishUseCase);

        var response = function.publishDailyDigestHttp(
                new FakeHttpRequestMessage<>(Optional.empty()),
                new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().toString()).contains("Failed to publish daily digest: boom");
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
            return "Fun_FacebookPublish";
        }
    }

    private static final class FakeHttpRequestMessage<T>
            implements HttpRequestMessage<Optional<T>> {

        private final Optional<T> body;

        private FakeHttpRequestMessage(final Optional<T> body) {
            this.body = body;
        }

        @Override
        public URI getUri() {
            return URI.create("https://localhost/api/Fun_FacebookPublishStart");
        }

        @Override
        public HttpMethod getHttpMethod() {
            return HttpMethod.POST;
        }

        @Override
        public Map<String, String> getHeaders() {
            return Map.of();
        }

        @Override
        public Map<String, String> getQueryParameters() {
            return Map.of();
        }

        @Override
        public Optional<T> getBody() {
            return this.body;
        }

        @Override
        public HttpResponseMessage.Builder createResponseBuilder(final HttpStatus status) {
            return new FakeHttpResponseBuilder().status(status);
        }

        @Override
        public HttpResponseMessage.Builder createResponseBuilder(final HttpStatusType status) {
            return new FakeHttpResponseBuilder().status(status);
        }
    }

    private static final class FakeHttpResponseBuilder
            implements HttpResponseMessage.Builder {

        private HttpStatusType status = HttpStatus.OK;
        private final java.util.LinkedHashMap<String, String> headers =
                new java.util.LinkedHashMap<>();
        private Object body;

        @Override
        public HttpResponseMessage.Builder status(final HttpStatusType value) {
            this.status = value;
            return this;
        }

        @Override
        public HttpResponseMessage.Builder header(final String key, final String value) {
            this.headers.put(key, value);
            return this;
        }

        @Override
        public HttpResponseMessage.Builder body(final Object value) {
            this.body = value;
            return this;
        }

        @Override
        public HttpResponseMessage build() {
            return new FakeHttpResponseMessage(this.status, this.headers, this.body);
        }
    }

    private static final class FakeHttpResponseMessage
            implements HttpResponseMessage {

        private final HttpStatusType status;
        private final Map<String, String> headers;
        private final Object body;

        private FakeHttpResponseMessage(final HttpStatusType status,
                final Map<String, String> headers,
                final Object body) {
            this.status = status;
            this.headers = Map.copyOf(headers);
            this.body = body;
        }

        @Override
        public HttpStatusType getStatus() {
            return this.status;
        }

        @Override
        public String getHeader(final String key) {
            return this.headers.get(key);
        }

        @Override
        public Object getBody() {
            return this.body;
        }
    }
}