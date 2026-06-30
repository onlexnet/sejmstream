package onlexnet.infra.adapters.in.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import onlexnet.app.ports.in.AdminUseCase;
import onlexnet.app.ports.in.admin.AdminAction;
import onlexnet.app.ports.in.admin.AdminCommandRequest;
import onlexnet.app.ports.in.admin.AdminOutcome;
import onlexnet.sejmapi.telegram.TelegramNotifier;

class TelegramBotFunctionsTest {

    @Test
    void givenWebhookFunction_whenInspectingTrigger_thenPostAnonymousAndRouteAreConfigured()
            throws NoSuchMethodException {
        var method = TelegramBotFunctions.class.getDeclaredMethod(
                "telegramWebhook",
                HttpRequestMessage.class,
                ExecutionContext.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(HttpTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo("Fun_TelegramWebhook");
        assertThat(trigger).isNotNull();
        assertThat(trigger.methods()).containsExactly(HttpMethod.POST);
        assertThat(trigger.authLevel()).isEqualTo(AuthorizationLevel.ANONYMOUS);
        assertThat(trigger.route()).isEqualTo("telegram/webhook");
    }

    @Test
    void givenValidTelegramPayload_whenWebhookInvoked_thenDelegatesAndReturnsOk() {
        var adminUseCase = mock(AdminUseCase.class);
        var actionParser = new TelegramAdminActionParser();
        var outcomePresenter = new TelegramAdminOutcomePresenter();
        var telegramNotifier = mock(TelegramNotifier.class);
        var functions = new TelegramBotFunctions(
            adminUseCase,
            actionParser,
            outcomePresenter,
            telegramNotifier,
            new ObjectMapper());

        when(adminUseCase.handleAdminAction(any(AdminCommandRequest.class)))
            .thenReturn(new AdminOutcome.HelpOverview());

        var payload = """
                {
                  "update_id": 1,
                  "message": {
                    "message_id": 22,
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

        var requestCaptor = ArgumentCaptor.forClass(AdminCommandRequest.class);
        verify(adminUseCase).handleAdminAction(requestCaptor.capture());
        assertThat(requestCaptor.getValue().action()).isEqualTo(AdminAction.Help.INSTANCE);

        verify(telegramNotifier).sendMessage(eq(1001L), any(String.class));
    }

    @Test
    void givenNoReplyCommandResult_whenWebhookInvoked_thenSkipsSendingMessage() {
        var adminUseCase = mock(AdminUseCase.class);
        var actionParser = new TelegramAdminActionParser();
        var outcomePresenter = new TelegramAdminOutcomePresenter();
        var telegramNotifier = mock(TelegramNotifier.class);
        var functions = new TelegramBotFunctions(
            adminUseCase,
            actionParser,
            outcomePresenter,
            telegramNotifier,
            new ObjectMapper());

        when(adminUseCase.handleAdminAction(any(AdminCommandRequest.class)))
            .thenReturn(new AdminOutcome.NoopIgnored());

        var payload = """
                {
                  "update_id": 1,
                  "message": {
                    "message_id": 22,
                    "chat": {
                      "id": 1001,
                      "type": "private"
                    },
                    "text": "   "
                  }
                }
                """;

        var response = functions.telegramWebhook(
                new FakeHttpRequestMessage<>(Optional.of(payload)),
                new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("OK");
        verify(adminUseCase).handleAdminAction(any(AdminCommandRequest.class));
        verify(telegramNotifier, never()).sendMessage(eq(1001L), any(String.class));
    }

        @Test
        void givenDeferredCommandResult_whenWebhookInvoked_thenSendsDeferredAcknowledgement() {
        var adminUseCase = mock(AdminUseCase.class);
        var actionParser = new TelegramAdminActionParser();
        var outcomePresenter = new TelegramAdminOutcomePresenter();
        var telegramNotifier = mock(TelegramNotifier.class);
        var functions = new TelegramBotFunctions(
                                adminUseCase,
                                actionParser,
                                outcomePresenter,
                                telegramNotifier,
                                new ObjectMapper());

        when(adminUseCase.handleAdminAction(any(AdminCommandRequest.class)))
            .thenReturn(new AdminOutcome.ActionDeferred("corr-123"));

        var payload = """
                                {
                                    "update_id": 1,
                                    "message": {
                                        "message_id": 22,
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
        verify(telegramNotifier).sendMessage(eq(1001L), contains("corr-123"));
        }

    @Test
    void givenMalformedPayload_whenWebhookInvoked_thenReturnsOkAndSkipsDelegation() {
        var adminUseCase = mock(AdminUseCase.class);
        var actionParser = new TelegramAdminActionParser();
        var outcomePresenter = new TelegramAdminOutcomePresenter();
        var telegramNotifier = mock(TelegramNotifier.class);
        var functions = new TelegramBotFunctions(
            adminUseCase,
            actionParser,
            outcomePresenter,
            telegramNotifier,
            new ObjectMapper());

        var response = functions.telegramWebhook(
                new FakeHttpRequestMessage<>(Optional.of("{broken-json")),
                new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("OK");
        verify(adminUseCase, never()).handleAdminAction(any(AdminCommandRequest.class));
        verify(telegramNotifier, never()).sendMessage(eq(1001L), any(String.class));
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