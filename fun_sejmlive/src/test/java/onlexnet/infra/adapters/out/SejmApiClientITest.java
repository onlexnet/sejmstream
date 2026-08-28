package onlexnet.infra.adapters.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
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
    private static final String INTERPELLATION_NUM = "32472";
    private static final String REPLY_KEY = "CDUH9D";
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

        assertListCallReachable(() -> this.sejmApiClient.fetchVotingsForDate(activeTermNum, referenceDate));
        assertListCallReachable(() -> this.sejmApiClient.fetchCommitteeSittingsForDate(activeTermNum, referenceDate));
        assertListCallReachable(() -> this.sejmApiClient.fetchPrintsModifiedSince(activeTermNum, referenceDate));
        assertListCallReachable(() -> this.sejmApiClient.fetchInterpellationsModifiedSince(activeTermNum, referenceDateTime));
        assertListCallReachable(() -> this.sejmApiClient.fetchWrittenQuestionsModifiedSince(activeTermNum, referenceDateTime));
        assertListCallReachable(() -> this.sejmApiClient.fetchBillsReceivedSince(activeTermNum, referenceDate));
    }

    @Test
    void givenKnownInterpellationReplyBodyEndpoint_whenFetching_thenDownloadAndDeserializeHtmlBody() {
        var interpellationsApi = new InterpellationsApi(createApiClientForHtmlStringResponses());

        var htmlBody = assertCallReachable(
                () -> interpellationsApi.sejmTermtermInterpellationsNumReplyKeyBodyGet(
                        REPLY_KEY,
                        INTERPELLATION_NUM,
                        TERM_9));

        assertThat(htmlBody).isNotBlank();
        assertThat(htmlBody).contains("<");
    }

    @Test
    void givenKnownInterpellationAttachmentEndpoint_whenFetching_thenDownloadAttachmentContent() {
        var attachmentFetchResult = this.sejmApiClient.fetchAttachmentText(
                TERM_9,
                ATTACHMENT_REPLY_KEY,
                ATTACHMENT_FILE_NAME);

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

    private static void assertListCallReachable(Supplier<?> apiCall) {
        assertThat(assertCallReachable(apiCall)).isNotNull();
    }

    private static <T> T assertCallReachable(Supplier<T> apiCall) {
        RuntimeException lastFailure = null;

        for (var attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return apiCall.get();
            } catch (RestClientException | IllegalStateException exception) {
                lastFailure = exception;
            }
        }

        throw new AssertionError("Sejm API method did not succeed after retries", lastFailure);
    }
}
