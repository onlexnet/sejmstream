package onlexnet.infra.adapters.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClientException;

import com.restfb.FacebookClient;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.infra.adapters.out.sejm.generated.api.InterpellationsApi;
import onlexnet.infra.adapters.out.sejm.generated.core.ApiClient;
import onlexnet.testsupport.AppTest;
import onlexnet.testsupport.PostgresIntegrationTestSupport;

@AppTest
class SejmApiClientITest extends PostgresIntegrationTestSupport {

    private static final int MAX_ATTEMPTS = 3;
    private static final int TERM_9 = 9;
    private static final String ATTACHMENT_REPLY_KEY = "ATTCDUH9D";
    private static final String ATTACHMENT_FILE_NAME = "i32472-o1.pdf";

    @MockitoBean
    FacebookClient facebookClient;

    @Autowired
    private DefaultSejmApiClient sejmApiClient;

    @Test
    void givenSpringContext_whenResolvingAndCallingSejmApiClient_thenBeanIsCreatedAndApiIsReachable() {
        assertThat(this.sejmApiClient).isNotNull();

        var terms = this.sejmApiClient.fetchTerms();
        assertThat(terms).isNotNull();
        assertThat(terms).isNotEmpty();

        var activeTermNum = terms.stream()
                .filter(SejmApiClient.SejmTerm::current)
                .map(SejmApiClient.SejmTerm::num)
                .findFirst()
                .orElse(terms.getFirst().num());

        var referenceDate = LocalDate.now().minusDays(7);
        var referenceDateTime = LocalDateTime.now().minusDays(7);

        assertListCallReachable("fetchVotingsForDate", () -> this.sejmApiClient.fetchVotingsForDate(activeTermNum, referenceDate));
        assertListCallReachable("fetchCommitteeSittingsForDate", () -> this.sejmApiClient.fetchCommitteeSittingsForDate(activeTermNum, referenceDate));
        assertListCallReachable("fetchPrintsModifiedSince", () -> this.sejmApiClient.fetchPrintsModifiedSince(activeTermNum, referenceDate));
        assertListCallReachable(
            "fetchInterpellationsModifiedSince",
            () -> this.sejmApiClient.fetchInterpellationsModifiedSince(activeTermNum, referenceDateTime));
        assertListCallReachable(
            "fetchWrittenQuestionsModifiedSince",
            () -> this.sejmApiClient.fetchWrittenQuestionsModifiedSince(activeTermNum, referenceDateTime));
        assertListCallReachable("fetchBillsReceivedSince", () -> this.sejmApiClient.fetchBillsReceivedSince(activeTermNum, referenceDate));
    }

    @Test
    void givenCurrentInterpellationReplyBodyEndpoint_whenFetching_thenDownloadAndDeserializeHtmlBody() {
        var activeTerm = assertCallReachable("fetchTerms", this.sejmApiClient::fetchTerms).stream()
            .filter(SejmApiClient.SejmTerm::current)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Sejm API did not return an active term"));
        var interpellation = assertCallReachable(
            "fetchInterpellationsModifiedSince",
            () -> this.sejmApiClient.fetchInterpellationsModifiedSince(activeTerm.num(), activeTerm.from().atStartOfDay()))
            .stream()
            .filter(candidate -> candidate.replies().stream()
                .anyMatch(SejmApiClient.ReplyItem.ActualReply.class::isInstance))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Sejm API did not return an interpellation with a reply"));
        var reply = interpellation.replies().stream()
            .filter(SejmApiClient.ReplyItem.ActualReply.class::isInstance)
            .map(SejmApiClient.ReplyItem.ActualReply.class::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Interpellation does not contain an actual reply"));
        var interpellationsApi = new InterpellationsApi(createApiClientForHtmlStringResponses());

        var htmlBody = assertCallReachable(
            "sejmTermtermInterpellationsNumReplyKeyBodyGet",
            () -> interpellationsApi.sejmTermtermInterpellationsNumReplyKeyBodyGet(
                reply.key(),
                String.valueOf(interpellation.num()),
                activeTerm.num()));

        assertThat(htmlBody).isNotBlank();
        assertThat(htmlBody).contains("<");
    }

    @Test
    void givenKnownInterpellationAttachmentEndpoint_whenFetching_thenDownloadAttachmentContent() {
        var attachmentFetchResult = assertCallReachable(
            "fetchAttachmentText",
            () -> this.sejmApiClient.fetchAttachmentText(
                TERM_9,
                ATTACHMENT_REPLY_KEY,
                ATTACHMENT_FILE_NAME));

        assertThat(attachmentFetchResult)
                .isInstanceOfAny(
                        SejmApiClient.AttachmentFetchResult.PdfText.class,
                        SejmApiClient.AttachmentFetchResult.Unsupported.class,
                        SejmApiClient.AttachmentFetchResult.Unavailable.class);

        switch (attachmentFetchResult) {
            case SejmApiClient.AttachmentFetchResult.PdfText pdfText -> {
                assertThat(pdfText.replyKey()).isEqualTo(ATTACHMENT_REPLY_KEY);
                assertThat(pdfText.fileName()).isEqualTo(ATTACHMENT_FILE_NAME);
                assertThat(pdfText.mimeType()).isEqualTo("application/pdf");
                assertThat(pdfText.text()).isNotBlank();
                assertThat(pdfText.sizeBytes()).isGreaterThan(0);
            }
            case SejmApiClient.AttachmentFetchResult.Unsupported unsupported -> {
                assertThat(unsupported.replyKey()).isEqualTo(ATTACHMENT_REPLY_KEY);
                assertThat(unsupported.fileName()).isEqualTo(ATTACHMENT_FILE_NAME);
                assertThat(unsupported.mimeType()).isNotBlank();
                assertThat(unsupported.sizeBytes()).isGreaterThan(0);
                assertThat(unsupported.reason()).isNotBlank();
            }
            case SejmApiClient.AttachmentFetchResult.Unavailable unavailable -> {
                assertThat(unavailable.replyKey()).isEqualTo(ATTACHMENT_REPLY_KEY);
                assertThat(unavailable.fileName()).isEqualTo(ATTACHMENT_FILE_NAME);
                assertThat(unavailable.reason()).isNotBlank();
            }
        }
    }

    private static ApiClient createApiClientForHtmlStringResponses() {
        var mapper = ApiClient.createDefaultMapper(ApiClient.createDefaultDateFormat());
        var restClient = ApiClient.buildRestClientBuilder(mapper)
                .configureMessageConverters(builder -> {
                    builder.addCustomConverter(new JacksonJsonHttpMessageConverter(mapper));
                    builder.addCustomConverter(new StringHttpMessageConverter());
                })
                .build();
        return new ApiClient(restClient, mapper, ApiClient.createDefaultDateFormat());
    }

    private static void assertListCallReachable(String operationName, Supplier<?> apiCall) {
        assertThat(assertCallReachable(operationName, apiCall)).isNotNull();
    }

    private static <T> T assertCallReachable(String operationName, Supplier<T> apiCall) {
        RuntimeException lastFailure = null;

        for (var attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return apiCall.get();
            } catch (RestClientException | IllegalStateException exception) {
                lastFailure = exception;
            }
        }

        throw new TestAbortedException(
            "Skipping due to temporary Sejm API failure in " + operationName,
            lastFailure);
    }
}
