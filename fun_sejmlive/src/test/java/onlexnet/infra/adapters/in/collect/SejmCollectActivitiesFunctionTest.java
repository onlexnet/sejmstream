package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmApiClient.SejmPrints;
import onlexnet.app.ports.out.SejmApiClient.SejmTerm;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.app.ports.out.SejmDailyDigestPersistence;

class SejmCollectActivitiesFunctionTest {

    @Test
        void shouldReturnInterpellationSnapshotDataInActivityResult() {
        var collectService = mock(SejmCollectOperations.class);
                when(collectService.collectInterpellations(eq(10), any(LocalDate.class))).thenReturn(2);
        var sejmApiClient = mock(SejmApiClient.class);
        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                        new SejmPrints(10, null, "/term10/prints"),
                        null)));

        var digestPersistence = mock(SejmDailyDigestPersistence.class);
        List<Map<String, Object>> interpellationRows = List.of(
                new TreeMap<String, Object>(Map.of("item_key", "78", "item_json", "{\"num\":78,\"title\":\"B\"}")),
                new TreeMap<String, Object>(Map.of("item_key", "77", "item_json", "{\"num\":77,\"title\":\"A\"}")));
        List<Map<String, Object>> questionRows = List.of(
                new TreeMap<String, Object>(Map.of("item_key", "302")),
                new TreeMap<String, Object>(Map.of("item_key", "301")));
        List<Map<String, Object>> printRows = List.of(
                new TreeMap<String, Object>(Map.of("item_key", "402")),
                new TreeMap<String, Object>(Map.of("item_key", "401")));
        List<Map<String, Object>> billRows = List.of(
                new TreeMap<String, Object>(Map.of("item_key", "502")),
                new TreeMap<String, Object>(Map.of("item_key", "501")));

        when(digestPersistence.findByDateAndType(any(LocalDate.class), eq("INTERPELLATION"))).thenReturn(interpellationRows);
        when(digestPersistence.findByDateAndType(any(LocalDate.class), eq("WRITTEN_QUESTION"))).thenReturn(questionRows);
        when(digestPersistence.findByDateAndType(any(LocalDate.class), eq("PRINT"))).thenReturn(printRows);
        when(digestPersistence.findByDateAndType(any(LocalDate.class), eq("BILL"))).thenReturn(billRows);

        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient, digestPersistence);

        var result = support.collectInterpellations(null, new SejmCollectFunctionTestSupport.FakeExecutionContext());

        assertThat(result.count()).isEqualTo(2);
        assertThat(result.termNum()).isEqualTo(10);
        assertThat(result.collectionDate()).isNotNull();
        assertThat(result.interpellationFingerprints())
                .containsEntry("77", sha256Hex("{\"num\":77,\"title\":\"A\"}"))
                .containsEntry("78", sha256Hex("{\"num\":78,\"title\":\"B\"}"));
        assertThat(result.itemKeys()).containsExactly("77", "78");

        verify(digestPersistence, times(1)).findByDateAndType(any(LocalDate.class), eq("INTERPELLATION"));
        verify(collectService, times(1)).collectInterpellations(eq(10), any(LocalDate.class));
    }

    @Test
    void givenActivities_whenInvoked_thenDelegateToServiceUsingCurrentTermAndToday() {
        var collectService = mock(SejmCollectOperations.class);
        when(collectService.collectVotings(eq(10), any(LocalDate.class))).thenReturn(11);
        when(collectService.collectBills(eq(10), any(LocalDate.class))).thenReturn(22);
        var sejmApiClient = mock(SejmApiClient.class);
        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(false, LocalDate.of(2023, 1, 1), 9,
                        new SejmPrints(0, null, "/term9/prints"),
                        LocalDate.of(2023, 10, 10)),
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                        new SejmPrints(10, null, "/term10/prints"),
                        null)));
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);

        var beforeVotings = LocalDate.now();
        var votingResult = support.collectVotings(null, new SejmCollectFunctionTestSupport.FakeExecutionContext());
        var afterVotings = LocalDate.now();

        var beforeBills = LocalDate.now();
        var billsResult = support.collectBills(null, new SejmCollectFunctionTestSupport.FakeExecutionContext());
        var afterBills = LocalDate.now();

        assertThat(votingResult.count()).isEqualTo(11);
        assertThat(billsResult.count()).isEqualTo(22);
        var votingsDateCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        var billsDateCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        verify(collectService, times(1)).collectVotings(eq(10), votingsDateCaptor.capture());
        verify(collectService, times(1)).collectBills(eq(10), billsDateCaptor.capture());
        assertThat(votingsDateCaptor.getValue()).isBetween(beforeVotings, afterVotings);
        assertThat(billsDateCaptor.getValue()).isBetween(beforeBills, afterBills);
        verify(sejmApiClient, times(1)).fetchTerms();
    }

    @Test
    void givenNoCurrentTerm_whenRunningActivity_thenWrapsAsIllegalStateException() {
        var collectService = mock(SejmCollectOperations.class);
        var sejmApiClient = mock(SejmApiClient.class);
        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(false, LocalDate.of(2023, 1, 1), 9,
                        new SejmPrints(0, null, "/term9/prints"),
                        LocalDate.of(2023, 10, 10))));
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);

        assertThatThrownBy(() -> support.collectCommittees(null, new SejmCollectFunctionTestSupport.FakeExecutionContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Failed to collect committee sittings")
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessageContaining("No current Sejm term found");
    }

    @Test
    void givenServiceThrows_whenCollectQuestions_thenWrapsAsIllegalStateException() {
        var collectService = mock(SejmCollectOperations.class);
        when(collectService.collectWrittenQuestions(eq(10), any(LocalDate.class)))
                .thenThrow(new RuntimeException("service failure"));
        var sejmApiClient = mock(SejmApiClient.class);
        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                        new SejmPrints(10, null, "/term10/prints"),
                        null)));
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);

        assertThatThrownBy(() -> support.collectQuestions(null, new SejmCollectFunctionTestSupport.FakeExecutionContext()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Failed to collect written questions")
                .hasCauseInstanceOf(RuntimeException.class)
                .cause()
                .hasMessage("service failure");
    }

    @Test
    void givenServiceThrows_whenCollectBills_thenReturnsZeroInsteadOfFailingOrchestration() {
        var collectService = mock(SejmCollectOperations.class);
        when(collectService.collectBills(eq(10), any(LocalDate.class)))
                .thenThrow(new RuntimeException("sejm api timeout"));
        var sejmApiClient = mock(SejmApiClient.class);
        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                        new SejmPrints(10, null, "/term10/prints"),
                        null)));
        var support = SejmCollectFunctionTestSupport.newSupport(collectService, sejmApiClient);

        var result = support.collectBills(null, new SejmCollectFunctionTestSupport.FakeExecutionContext());

        assertThat(result.count()).isEqualTo(0);
        verify(collectService, times(1)).collectBills(eq(10), any(LocalDate.class));
    }

        private static String sha256Hex(String value) {
                try {
                        var digest = MessageDigest.getInstance("SHA-256");
                        var hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
                        var hex = new StringBuilder(hash.length * 2);
                        for (byte b : hash) {
                                hex.append(String.format("%02x", b));
                        }
                        return hex.toString();
                } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException("SHA-256 is not available", e);
                }
        }
}
