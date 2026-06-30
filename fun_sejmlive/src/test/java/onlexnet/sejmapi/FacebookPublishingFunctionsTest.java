package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.LocalDate;
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
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import onlexnet.app.ports.out.FacebookPublisher;
import onlexnet.app.ports.out.SejmDailyDigestPersistence;

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
    void givenAlreadyPublishedToday_whenInvoked_thenSkipsPublishing() {
        var publisher = mock(FacebookPublisher.class);
        var digestService = new StubSejmDigestService(Optional.of("digest msg"));
        var repository = new RecordingRepository();
        repository.alreadyPublished = true;

        var function = new FacebookPublishingFunctions(publisher, digestService, repository);
        function.publishDailyDigest("timer", new FakeExecutionContext());

        assertThat(repository.alreadyPublishedChecks).isEqualTo(1);
        assertThat(digestService.buildCalls).isEqualTo(0);
        verify(publisher, never()).publish(anyString());
        assertThat(repository.insertCalls).isEqualTo(0);
    }

    @Test
    void givenDigestAvailable_whenInvoked_thenPublishesAndWritesSuccessLog() {
        var publisher = mock(FacebookPublisher.class);
        var digestService = new StubSejmDigestService(Optional.of("digest msg"));
        var repository = new RecordingRepository();

        var function = new FacebookPublishingFunctions(publisher, digestService, repository);

        function.publishDailyDigest("timer", new FakeExecutionContext());

        assertThat(repository.alreadyPublishedChecks).isEqualTo(1);
        assertThat(digestService.buildCalls).isEqualTo(1);
        verify(publisher).publish("digest msg");
        assertThat(repository.insertCalls).isEqualTo(1);
        assertThat(repository.lastMessage).isEqualTo("digest msg");
        assertThat(repository.lastSuccess).isTrue();
        assertThat(repository.lastErrorMessage).isNull();
    }

    @Test
    void givenNoDigestAvailable_whenInvoked_thenSkipsPublishing() {
        var publisher = mock(FacebookPublisher.class);
        var digestService = new StubSejmDigestService(Optional.empty());
        var repository = new RecordingRepository();

        var function = new FacebookPublishingFunctions(publisher, digestService, repository);

        function.publishDailyDigest("timer", new FakeExecutionContext());

        assertThat(digestService.buildCalls).isEqualTo(1);
        verify(publisher, never()).publish(anyString());
        assertThat(repository.insertCalls).isEqualTo(0);
    }

    @Test
    void givenPublisherThrows_whenInvoked_thenWritesFailureLogAndRethrows() {
        var publisher = mock(FacebookPublisher.class);
        var digestService = new StubSejmDigestService(Optional.of("digest msg"));
        var repository = new RecordingRepository();
        var failure = new IllegalStateException("boom");
        org.mockito.Mockito.doThrow(failure).when(publisher).publish("digest msg");

        var function = new FacebookPublishingFunctions(publisher, digestService, repository);

        assertThatThrownBy(() -> function.publishDailyDigest("timer", new FakeExecutionContext()))
                .isSameAs(failure);

        assertThat(repository.insertCalls).isEqualTo(1);
        assertThat(repository.lastMessage).isNull();
        assertThat(repository.lastSuccess).isFalse();
        assertThat(repository.lastErrorMessage).isEqualTo("boom");
    }

    @Test
    void givenDigestServiceThrows_whenInvoked_thenWritesFailureLogAndRethrows() {
        var publisher = mock(FacebookPublisher.class);
        var digestService = new FailingStubSejmDigestService();
        var repository = new RecordingRepository();

        var function = new FacebookPublishingFunctions(publisher, digestService, repository);

        assertThatThrownBy(() -> function.publishDailyDigest("timer", new FakeExecutionContext()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("digest build failed");

        assertThat(repository.insertCalls).isEqualTo(1);
        assertThat(repository.lastMessage).isNull();
        assertThat(repository.lastSuccess).isFalse();
        assertThat(repository.lastErrorMessage).isEqualTo("digest build failed");
    }

    @Test
    void givenPublisherThrowsAndLogInsertThrows_whenInvoked_thenRethrownExceptionPreserved() {
        var publisher = mock(FacebookPublisher.class);
        var digestService = new StubSejmDigestService(Optional.of("digest msg"));
        var repository = new FailingRecordingRepository();
        var publishFailure = new IllegalStateException("publish failed");
        org.mockito.Mockito.doThrow(publishFailure).when(publisher).publish("digest msg");

        var function = new FacebookPublishingFunctions(publisher, digestService, repository);

        // Original exception should be rethrown, not the log failure
        assertThatThrownBy(() -> function.publishDailyDigest("timer", new FakeExecutionContext()))
                .isSameAs(publishFailure)
                .satisfies(ex -> {
                    var suppressed = ex.getSuppressed();
                    assertThat(suppressed).hasSize(1);
                    assertThat(suppressed[0]).isInstanceOf(UnsupportedOperationException.class);
                });
    }

    @Test
    void givenHttpTriggerAndDigestAvailable_whenInvoked_thenReturnsPublishedStatus() {
        var publisher = mock(FacebookPublisher.class);
        var digestService = new StubSejmDigestService(Optional.of("digest msg"));
        var repository = new RecordingRepository();
        var function = new FacebookPublishingFunctions(publisher, digestService, repository);

        var response = function.publishDailyDigestHttp(
                new FakeHttpRequestMessage<>(Optional.empty()),
                new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().toString()).contains("PUBLISHED");
        verify(publisher).publish("digest msg");
        assertThat(repository.insertCalls).isEqualTo(1);
        assertThat(repository.lastSuccess).isTrue();
    }

    @Test
    void givenHttpTriggerAndAlreadyPublished_whenInvoked_thenReturnsSkippedStatus() {
        var publisher = mock(FacebookPublisher.class);
        var digestService = new StubSejmDigestService(Optional.of("digest msg"));
        var repository = new RecordingRepository();
        repository.alreadyPublished = true;
        var function = new FacebookPublishingFunctions(publisher, digestService, repository);

        var response = function.publishDailyDigestHttp(
                new FakeHttpRequestMessage<>(Optional.empty()),
                new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().toString()).contains("SKIPPED_ALREADY_PUBLISHED");
        verify(publisher, never()).publish(anyString());
        assertThat(repository.insertCalls).isEqualTo(0);
    }

    @Test
    void givenHttpTriggerAndPublisherFailure_whenInvoked_thenReturnsInternalServerError() {
        var publisher = mock(FacebookPublisher.class);
        var digestService = new StubSejmDigestService(Optional.of("digest msg"));
        var repository = new RecordingRepository();
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
                .when(publisher)
                .publish("digest msg");
        var function = new FacebookPublishingFunctions(publisher, digestService, repository);

        var response = function.publishDailyDigestHttp(
                new FakeHttpRequestMessage<>(Optional.empty()),
                new FakeExecutionContext());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().toString()).contains("Failed to publish daily digest: boom");
        assertThat(repository.insertCalls).isEqualTo(1);
        assertThat(repository.lastSuccess).isFalse();
    }

    private static final class StubSejmDigestService extends SejmDigestService {

        private final Optional<String> digest;
        private int buildCalls;

        StubSejmDigestService(final Optional<String> digest) {
            super(new RecordingRepository(), new ObjectMapper());
            this.digest = digest;
        }

        @Override
        public Optional<String> buildDigest(final LocalDate date) {
            this.buildCalls++;
            return this.digest;
        }
    }

    private static final class FailingStubSejmDigestService extends SejmDigestService {

        private static final RuntimeException FAILURE = new RuntimeException("digest build failed");

        FailingStubSejmDigestService() {
            super(new RecordingRepository(), new ObjectMapper());
        }

        @Override
        public Optional<String> buildDigest(final LocalDate date) {
            throw FAILURE;
        }
    }

    private static class RecordingRepository implements SejmDailyDigestPersistence {

        private boolean alreadyPublished;
        private int alreadyPublishedChecks;
        private int insertCalls;
        private String lastMessage;
        private boolean lastSuccess;
        private String lastErrorMessage;

        @Override
        public int upsertItem(final LocalDate date, final String dataType,
                final String itemKey, final String title, final String itemJson) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public java.util.List<Map<String, Object>> findByDate(final LocalDate date) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public java.util.List<Map<String, Object>> findByDateAndType(final LocalDate date,
                final String dataType) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public boolean alreadyPublishedToday(final LocalDate date) {
            this.alreadyPublishedChecks++;
            return this.alreadyPublished;
        }

        @Override
        public int insertPublishLog(final LocalDate date, final String message,
                final boolean success, final String errorMsg) {
            this.insertCalls++;
            this.lastMessage = message;
            this.lastSuccess = success;
            this.lastErrorMessage = errorMsg;
            return 1;
        }
    }

    private static final class FailingRecordingRepository extends RecordingRepository {

        @Override
        public int insertPublishLog(final LocalDate date, final String message,
                final boolean success, final String errorMsg) {
            throw new UnsupportedOperationException("Log insertion failed");
        }
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
