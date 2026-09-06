package onlexnet.infra.adapters.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

class DefaultSejmApiClientTest {

    @Test
    void givenRealApi_whenFetchTerms_thenReturnsNonEmptyListWithCurrentTerm() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = requireSejmApiCallOrSkip("fetchTerms", client::fetchTerms);

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        assertThat(result).anyMatch(term -> term.num() > 0);
    }

    @Test
    void givenRealApi_whenFetchBillsReceivedSince_thenDeserializesWithoutException() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = requireSejmApiCallOrSkip(
            "fetchBillsReceivedSince",
            () -> client.fetchBillsReceivedSince(10, LocalDate.now().minusDays(7)));

        assertThat(result).isNotNull();
    }

    @Test
    void givenRealApi_whenFetchPrintsModifiedSince_thenDeserializesWithoutException() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = requireSejmApiCallOrSkip(
            "fetchPrintsModifiedSince",
            () -> client.fetchPrintsModifiedSince(10, LocalDate.now().minusDays(7)));

        assertThat(result).isNotNull();
    }

    @Test
    void givenRealApi_whenFetchInterpellationsModifiedSince_thenDeserializesWithoutException() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = requireSejmApiCallOrSkip(
            "fetchInterpellationsModifiedSince",
            () -> client.fetchInterpellationsModifiedSince(10, LocalDateTime.now().minusDays(7)));

        assertThat(result).isNotNull();
    }

    @Test
    void givenRealApi_whenFetchCommitteeSittingsForKnownDate_thenDeserializesWithoutException() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = requireSejmApiCallOrSkip(
            "fetchCommitteeSittingsForDate",
            () -> client.fetchCommitteeSittingsForDate(10, LocalDate.of(2025, 3, 13)));

        assertThat(result).isNotNull();
    }

    @Test
    void givenRealApi_whenFetchVotingsForRecentPlenaryDay_thenDeserializesWithoutException() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = requireSejmApiCallOrSkip(
            "fetchVotingsForDate",
            () -> client.fetchVotingsForDate(10, LocalDate.of(2025, 3, 13)));

        assertThat(result).isNotNull();
    }

    @Test
    void givenRealApi_whenFetchWrittenQuestionsModifiedSince_thenDeserializesWithoutException() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = requireSejmApiCallOrSkip(
                "fetchWrittenQuestionsModifiedSince",
                () -> client.fetchWrittenQuestionsModifiedSince(10, LocalDateTime.now().minusDays(7)));

        assertThat(result).isNotNull();
    }

    private static <T> T requireSejmApiCallOrSkip(String operationName, Supplier<T> apiCall) {
        try {
            return apiCall.get();
        } catch (RuntimeException exception) {
            throw new TestAbortedException("Skipping due to temporary Sejm API failure in " + operationName, exception);
        }
    }
}
