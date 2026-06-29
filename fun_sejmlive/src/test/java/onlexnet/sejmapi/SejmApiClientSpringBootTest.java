package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClientException;

import onlexnet.app.ports.out.SejmApiClient;

@AppTest(properties = "FB_TOKEN=test-placeholder-token-for-sejm-client-test")
class SejmApiClientSpringBootTest {

    private static final int MAX_ATTEMPTS = 3;

    @Autowired
    private SejmApiClient sejmApiClient;

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

    private static void assertListCallReachable(final Supplier<?> apiCall) {
        RuntimeException lastFailure = null;

        for (var attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                assertThat(apiCall.get()).isNotNull();
                return;
            } catch (RestClientException | IllegalStateException exception) {
                lastFailure = exception;
            }
        }

        throw new AssertionError("Sejm API method did not succeed after retries", lastFailure);
    }
}
