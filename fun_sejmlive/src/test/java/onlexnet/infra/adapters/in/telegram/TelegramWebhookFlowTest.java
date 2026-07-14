package onlexnet.infra.adapters.in.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;

import onlexnet.app.ports.in.collect.CollectDailyDigestUseCase;
import onlexnet.app.ports.in.publish.PublishDailyDigestUseCase;
import onlexnet.app.ports.out.AdminAccessPolicy;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.TelegramNotifier;
import onlexnet.app.usecases.DefaultAdminUseCase;

class TelegramWebhookFlowTest {

    @Test
    void givenHelpPayload_whenWebhookInvoked_thenMessageFlowWorksEndToEnd() {
        var sejmApiClient = mock(SejmApiClient.class);
        var collectDailyDigestUseCase = mock(CollectDailyDigestUseCase.class);
        var publishDailyDigestUseCase = mock(PublishDailyDigestUseCase.class);
        AdminAccessPolicy allowAllPolicy = (actor, action) -> true;

        var useCase = new DefaultAdminUseCase(
                sejmApiClient,
            collectDailyDigestUseCase,
            publishDailyDigestUseCase,
                allowAllPolicy,
                "test-version");

        var notifier = mock(TelegramNotifier.class);
        var functions = new TelegramBotFunctions(
                useCase,
                new TelegramAdminActionParser(),
                new TelegramAdminOutcomePresenter(),
                notifier,
                new ObjectMapper());

        var payload = """
                {
                  "update_id": 100,
                  "message": {
                    "message_id": 200,
                    "chat": {
                      "id": 1001,
                      "type": "private"
                    },
                    "text": "/help"
                  }
                }
                """;

        var response = functions.telegramWebhook(
                new FakeHttpRequestMessage<>(Optional.of(payload)),
                new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("OK");
        verify(notifier).sendMessage(eq(1001L), contains("/help - lista komend"));
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
            return "Fun_TelegramWebhook";
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
            return URI.create("https://localhost/api/telegram/webhook");
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
        public HttpResponseMessage.Builder header(final String key,
                final String value) {
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

    private static final class FakeHttpResponseMessage implements HttpResponseMessage {

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
