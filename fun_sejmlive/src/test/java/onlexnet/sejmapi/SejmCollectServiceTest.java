package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmDailyDigestPersistence;
import onlexnet.app.ports.out.SejmApiClient.BillItem;
import onlexnet.app.ports.out.SejmApiClient.CommitteeSittingItem;
import onlexnet.app.ports.out.SejmApiClient.InterpellationItem;
import onlexnet.app.ports.out.SejmApiClient.PrintItem;
import onlexnet.app.ports.out.SejmApiClient.VotingItem;
import onlexnet.app.ports.out.SejmApiClient.WrittenQuestionItem;

class SejmCollectServiceTest {

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 6, 13);

    @Test
    void givenNullDate_whenCollectVotings_thenThrowsNullPointerExceptionWithMessage() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);

        assertThatThrownBy(() -> service.collectVotings(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void givenNullDate_whenCollectCommitteeSittings_thenThrowsNullPointerExceptionWithMessage() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);

        assertThatThrownBy(() -> service.collectCommitteeSittings(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void givenNullDate_whenCollectPrints_thenThrowsNullPointerExceptionWithMessage() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);

        assertThatThrownBy(() -> service.collectPrints(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void givenNullDate_whenCollectInterpellations_thenThrowsNullPointerExceptionWithMessage() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);

        assertThatThrownBy(() -> service.collectInterpellations(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void givenNullDate_whenCollectWrittenQuestions_thenThrowsNullPointerExceptionWithMessage() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);

        assertThatThrownBy(() -> service.collectWrittenQuestions(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void givenNullDate_whenCollectBills_thenThrowsNullPointerExceptionWithMessage() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);

        assertThatThrownBy(() -> service.collectBills(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void givenVotingItems_whenCollectVotings_thenUpsertsExpectedKeysAndPayloads() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);
        var item1 = new VotingItem(
                LocalDateTime.of(2026, 6, 13, 10, 0),
                3,
                10,
                "Topic A",
                100,
                50,
                5,
                155,
                0);
        var item2 = new VotingItem(
                LocalDateTime.of(2026, 6, 13, 12, 0),
                3,
                11,
                "Topic B",
                110,
                40,
                5,
                155,
                0);
        when(sejmApiClient.fetchVotingsForDate(10, TEST_DATE))
            .thenReturn(Arrays.asList(item1, null, item2));

        var count = service.collectVotings(10, TEST_DATE);

        assertThat(count).isEqualTo(2);
        assertThat(repository.calls).hasSize(2);
        assertThat(repository.calls.get(0).date()).isEqualTo(TEST_DATE);
        assertThat(repository.calls.get(0).dataType()).isEqualTo("VOTING");
        assertThat(repository.calls.get(0).itemKey()).isEqualTo("3/10");
        assertThat(repository.calls.get(0).title()).isEqualTo("Topic A");
        assertThat(repository.calls.get(0).itemJson()).contains("\"topic\":\"Topic A\"");
        assertThat(repository.calls.get(1).itemKey()).isEqualTo("3/11");
        verify(sejmApiClient).fetchVotingsForDate(10, TEST_DATE);
    }

    @Test
    void givenNullCommitteeList_whenCollectCommitteeSittings_thenReturnsZero() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);
        when(sejmApiClient.fetchCommitteeSittingsForDate(10, TEST_DATE)).thenReturn(null);

        var count = service.collectCommitteeSittings(10, TEST_DATE);

        assertThat(count).isZero();
        assertThat(repository.calls).isEmpty();
        verify(sejmApiClient).fetchCommitteeSittingsForDate(10, TEST_DATE);
    }

    @Test
    void givenDate_whenCollectInterpellations_thenCallsApiWithMidnightDateTime() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);
        var expectedSince = LocalDateTime.of(TEST_DATE, LocalTime.MIDNIGHT);
        when(sejmApiClient.fetchInterpellationsModifiedSince(10, expectedSince))
                .thenReturn(List.of(new InterpellationItem(
                        77,
                        "Interpelacja testowa",
                        List.of("Ministerstwo"),
                        "2026-06-13",
                        "2026-06-13T00:00:00")));

        var count = service.collectInterpellations(10, TEST_DATE);

        assertThat(count).isEqualTo(1);
        assertThat(repository.calls).hasSize(1);
        assertThat(repository.calls.get(0).date()).isEqualTo(TEST_DATE);
        assertThat(repository.calls.get(0).dataType()).isEqualTo("INTERPELLATION");
        assertThat(repository.calls.get(0).itemKey()).isEqualTo("77");
        verify(sejmApiClient).fetchInterpellationsModifiedSince(10, expectedSince);
    }

    @Test
    void givenPrintItems_whenCollectPrints_thenCallsPrintApiAndUsesNumberAsKey() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);
        var item = new PrintItem(
                "123-A",
                "Projekt ustawy",
                LocalDateTime.of(2026, 6, 13, 10, 0),
                "2026-06-13");
        when(sejmApiClient.fetchPrintsModifiedSince(10, TEST_DATE)).thenReturn(List.of(item));

        var count = service.collectPrints(10, TEST_DATE);

        assertThat(count).isEqualTo(1);
        assertThat(repository.calls).hasSize(1);
        assertThat(repository.calls.get(0).dataType()).isEqualTo("PRINT");
        assertThat(repository.calls.get(0).itemKey()).isEqualTo(item.number());
        assertThat(repository.calls.get(0).title()).isEqualTo(item.title());
        verify(sejmApiClient).fetchPrintsModifiedSince(10, TEST_DATE);
    }

    @Test
    void givenBillItems_whenCollectBills_thenCallsBillsApiAndUsesNumberAsKey() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);
        var item = new BillItem(
                "UC-1",
                "Ustawa o testach",
                "2026-06-13",
                "Rządowy",
                "Nowy");
        when(sejmApiClient.fetchBillsReceivedSince(10, TEST_DATE)).thenReturn(List.of(item));

        var count = service.collectBills(10, TEST_DATE);

        assertThat(count).isEqualTo(1);
        assertThat(repository.calls).hasSize(1);
        assertThat(repository.calls.get(0).dataType()).isEqualTo("BILL");
        assertThat(repository.calls.get(0).itemKey()).isEqualTo(item.number());
        assertThat(repository.calls.get(0).title()).isEqualTo(item.title());
        verify(sejmApiClient).fetchBillsReceivedSince(10, TEST_DATE);
    }

    @Test
    void givenRepositoryFailure_whenCollectBills_thenWrapsExceptionWithContext() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);
        repository.failWith = new RuntimeException("database unavailable");
        when(sejmApiClient.fetchBillsReceivedSince(10, TEST_DATE))
                .thenReturn(List.of(new BillItem("UC-1", "Ustawa", "2026-06-13", "Rządowy", "Nowy")));

        assertThatThrownBy(() -> service.collectBills(10, TEST_DATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to collect bills")
                .hasCauseInstanceOf(RuntimeException.class)
                .cause()
                .hasMessage("database unavailable");
    }

    @Test
    void givenSerializationFailure_whenCollectPrints_thenWrapsAsIllegalStateException() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(final Object value)
                    throws JsonProcessingException {
                throw new JsonProcessingException("boom") {
                };
            }
        };
        objectMapper.registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);
        when(sejmApiClient.fetchPrintsModifiedSince(10, TEST_DATE))
                .thenReturn(List.of(new PrintItem(
                        "123",
                        "Projekt",
                        LocalDateTime.of(2026, 6, 13, 10, 0),
                        "2026-06-13")));

        assertThatThrownBy(() -> service.collectPrints(10, TEST_DATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to collect prints")
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessage("Failed to serialize item to JSON");
    }

    @Test
    void givenWrittenQuestionsWithNullItem_whenCollectWrittenQuestions_thenSkipsNullItems() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);
        var expectedSince = LocalDateTime.of(TEST_DATE, LocalTime.MIDNIGHT);
        when(sejmApiClient.fetchWrittenQuestionsModifiedSince(10, expectedSince))
            .thenReturn(Arrays.asList(
                        null,
                        new WrittenQuestionItem(
                                42,
                                "Pytanie testowe",
                                List.of("Minister"),
                                "2026-06-13",
                                "2026-06-13T08:30:00")));

        var count = service.collectWrittenQuestions(10, TEST_DATE);

        assertThat(count).isEqualTo(1);
        assertThat(repository.calls).hasSize(1);
        assertThat(repository.calls.get(0).dataType()).isEqualTo("WRITTEN_QUESTION");
        assertThat(repository.calls.get(0).itemKey()).isEqualTo("42");
        verify(sejmApiClient).fetchWrittenQuestionsModifiedSince(10, expectedSince);
    }

    @Test
    void givenSingleCommitteeSitting_whenCollecting_thenUsesCodeAndNumAsKey() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(sejmApiClient, repository, objectMapper);

        when(sejmApiClient.fetchCommitteeSittingsForDate(10, TEST_DATE))
                .thenReturn(List.of(new CommitteeSittingItem(
                        "KOMINF",
                        TEST_DATE,
                        12,
                        "Agenda",
                        "ODBYTA",
                        "101")));

        var count = service.collectCommitteeSittings(10, TEST_DATE);

        assertThat(count).isEqualTo(1);
        assertThat(repository.calls).hasSize(1);
        assertThat(repository.calls.get(0).date()).isEqualTo(TEST_DATE);
        assertThat(repository.calls.get(0).itemKey()).isEqualTo("KOMINF/12");
        assertThat(repository.calls.get(0).dataType()).isEqualTo("COMMITTEE_SITTING");
    }

    private static final class RecordingRepository implements SejmDailyDigestPersistence {

        private final List<UpsertCall> calls = new ArrayList<>();
        private RuntimeException failWith;

        @Override
        public int upsertItem(final LocalDate date, final String dataType,
                final String itemKey, final String title, final String itemJson) {
            if (this.failWith != null) {
                throw this.failWith;
            }
            this.calls.add(new UpsertCall(date, dataType, itemKey, title, itemJson));
            return 1;
        }

        @Override
        public List<java.util.Map<String, Object>> findByDate(final LocalDate date) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public List<java.util.Map<String, Object>> findByDateAndType(final LocalDate date,
                final String dataType) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public int insertPublishLog(final LocalDate date, final String message,
                final boolean success, final String errorMsg) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public boolean alreadyPublishedToday(final LocalDate date) {
            throw new UnsupportedOperationException("Not used by this test");
        }
    }

    private record UpsertCall(LocalDate date, String dataType, String itemKey,
            String title, String itemJson) {
    }
}