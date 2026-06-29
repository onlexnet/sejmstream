package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmApiClient.SejmTerm;

@AppTest
class SejmApiHttpFunctionsTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SejmApiHttpFunctions sejmApiHttpFunctions;

    @Autowired
    private SejmApiClient sejmApiClient;

    @Test
    void givenSimpleDataFunction_whenCheckingTriggerContract_thenItIsAnonymousAndRouteIsConfigured()
            throws NoSuchMethodException {
        var method = SejmApiHttpFunctions.class.getDeclaredMethod(
                "simpleData",
                HttpRequestMessage.class);

        var functionName = method.getAnnotation(FunctionName.class);
        var trigger = method.getParameters()[0].getAnnotation(HttpTrigger.class);

        assertThat(functionName).isNotNull();
        assertThat(functionName.value()).isEqualTo("Fun_SimpleData");
        assertThat(trigger).isNotNull();
        assertThat(trigger.methods()).containsExactly(HttpMethod.GET);
        assertThat(trigger.authLevel()).isEqualTo(AuthorizationLevel.ANONYMOUS);
        assertThat(trigger.route()).isEqualTo("sejm-api/simple");
    }

    @Test
    void givenSpringBootContext_whenStarting_thenRealSejmApiClientIsAvailable() {
        assertThat(this.applicationContext).isNotNull();
        assertThat(this.sejmApiClient).isNotNull();
        assertThat(this.sejmApiHttpFunctions).isNotNull();
    }

    @Test
    void givenRealSejmApiClient_whenInvokedThroughFunction_thenReturnsLivePayload() {
        var request = new FakeHttpRequestMessage<String>(Optional.empty(), Map.of());

        var response = this.sejmApiHttpFunctions.simpleData(request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeader("Content-Type"))
                .startsWith("application/json");
        assertThat(response.getBody()).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        var payload = (List<SejmTerm>) response.getBody();

        assertThat(payload).isNotEmpty();
        assertThat(payload)
                .allSatisfy(term -> {
                    assertThat(term.num()).isGreaterThan(0);
                    assertThat(term.from()).isNotNull();
                })
                .anySatisfy(term -> {
                    assertThat(term.current()).isTrue();
                    assertThat(term.prints()).isNotNull();
                });
    }

    private static final class FakeHttpRequestMessage<T>
            implements HttpRequestMessage<Optional<T>> {

        private final Optional<T> body;
        private final Map<String, String> queryParameters;

        private FakeHttpRequestMessage(final Optional<T> body,
                final Map<String, String> queryParameters) {
            this.body = body;
            this.queryParameters = queryParameters;
        }

        @Override
        public URI getUri() {
            return URI.create("https://localhost/api/sejm-api/simple");
        }

        @Override
        public HttpMethod getHttpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public Map<String, String> getHeaders() {
            return Map.of();
        }

        @Override
        public Map<String, String> getQueryParameters() {
            return this.queryParameters;
        }

        @Override
        public Optional<T> getBody() {
            return this.body;
        }

        @Override
        public HttpResponseMessage.Builder createResponseBuilder(
                final HttpStatus status) {
            return new FakeHttpResponseBuilder().status(status);
        }

        @Override
        public HttpResponseMessage.Builder createResponseBuilder(
                final HttpStatusType status) {
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
            return new FakeHttpResponseMessage(this.status, this.headers,
                    this.body);
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
