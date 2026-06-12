package onlexnet.infra.adapters.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class DefaultSejmApiClientTest {

    @Test
    void givenRealApi_whenFetchTerms_thenReturnsNonEmptyListWithCurrentTerm() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = client.fetchTerms();

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        assertThat(result).anyMatch(term -> term.num() > 0);
    }

    @Test
    void givenRealApi_whenFetchBillsReceivedSince_thenDeserializesWithoutException() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = client.fetchBillsReceivedSince(10, LocalDate.now().minusDays(7));

        assertThat(result).isNotNull();
    }

    @Test
    void givenRealApi_whenFetchPrintsModifiedSince_thenDeserializesWithoutException() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = client.fetchPrintsModifiedSince(10, LocalDate.now().minusDays(7));

        assertThat(result).isNotNull();
    }

    @Test
    void givenRealApi_whenFetchInterpellationsModifiedSince_thenDeserializesWithoutException() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = client.fetchInterpellationsModifiedSince(10, LocalDateTime.now().minusDays(7));

        assertThat(result).isNotNull();
    }

    @Test
    void givenRealApi_whenFetchCommitteeSittingsForKnownDate_thenDeserializesWithoutException() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = client.fetchCommitteeSittingsForDate(10, LocalDate.of(2025, 3, 13));

        assertThat(result).isNotNull();
    }

    @Test
    void givenRealApi_whenFetchVotingsForRecentPlenaryDay_thenDeserializesWithoutException() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = client.fetchVotingsForDate(10, LocalDate.of(2025, 3, 13));

        assertThat(result).isNotNull();
    }

    @Test
    void givenRealApi_whenFetchWrittenQuestionsModifiedSince_thenDeserializesWithoutException() {
        // Calls real Sejm API — requires network access
        var client = new DefaultSejmApiClient();

        var result = client.fetchWrittenQuestionsModifiedSince(10, LocalDateTime.now().minusDays(7));

        assertThat(result).isNotNull();
    }
}
