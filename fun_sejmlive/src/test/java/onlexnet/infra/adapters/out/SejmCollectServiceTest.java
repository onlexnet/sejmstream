package onlexnet.infra.adapters.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmDailyDigestPersistence;
import onlexnet.app.ports.out.InterpellationPublishQueueMessage;
import onlexnet.app.ports.out.InterpellationPublishQueuePort;
import onlexnet.app.ports.out.InterpellationPublishStatePort;
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
        var queuePort = new RecordingQueuePort();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            queuePort,
            repository,
            objectMapper);

        assertThatThrownBy(() -> service.collectVotings(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void givenNullDate_whenCollectCommitteeSittings_thenThrowsNullPointerExceptionWithMessage() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            new RecordingQueuePort(),
            repository,
            objectMapper);

        assertThatThrownBy(() -> service.collectCommitteeSittings(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void givenNullDate_whenCollectPrints_thenThrowsNullPointerExceptionWithMessage() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            new RecordingQueuePort(),
            repository,
            objectMapper);

        assertThatThrownBy(() -> service.collectPrints(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void givenNullDate_whenCollectInterpellations_thenThrowsNullPointerExceptionWithMessage() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            new RecordingQueuePort(),
            repository,
            objectMapper);

        assertThatThrownBy(() -> service.collectInterpellations(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void givenNullDate_whenCollectWrittenQuestions_thenThrowsNullPointerExceptionWithMessage() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            new RecordingQueuePort(),
            repository,
            objectMapper);

        assertThatThrownBy(() -> service.collectWrittenQuestions(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void givenNullDate_whenCollectBills_thenThrowsNullPointerExceptionWithMessage() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            new RecordingQueuePort(),
            repository,
            objectMapper);

        assertThatThrownBy(() -> service.collectBills(10, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void givenVotingItems_whenCollectVotings_thenUpsertsExpectedKeysAndPayloads() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            new RecordingQueuePort(),
            repository,
            objectMapper);
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
        var queuePort = new RecordingQueuePort();
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            queuePort,
            repository,
            objectMapper);
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
        var queuePort = new RecordingQueuePort();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            queuePort,
            repository,
            objectMapper);
        var expectedSince = LocalDateTime.of(TEST_DATE, LocalTime.MIDNIGHT);
        when(sejmApiClient.fetchInterpellationsModifiedSince(10, expectedSince))
                .thenReturn(List.of(InterpellationItem.missing(
                        77,
                        "Interpelacja testowa",
                        List.of("Ministerstwo"),
                        "2026-06-13",
                        "2026-06-13T00:00:00",
                        List.of(),
                        List.of())));

        var count = service.collectInterpellations(10, TEST_DATE);

        assertThat(count).isEqualTo(1);
        assertThat(repository.calls).hasSize(1);
        assertThat(repository.calls.get(0).date()).isEqualTo(TEST_DATE);
        assertThat(repository.calls.get(0).dataType()).isEqualTo("INTERPELLATION");
        assertThat(repository.calls.get(0).itemKey()).isEqualTo("77");
        assertThat(queuePort.enqueued).hasSize(1);
        assertThat(queuePort.enqueued.getFirst().message().domainMessageId())
            .isEqualTo("term-10-interpellation-77");
        assertThat(queuePort.enqueued.getFirst().message().attempt()).isEqualTo(1);
        assertThat(queuePort.enqueued.getFirst().visibilityDelay()).isEqualTo(Duration.ZERO);
        verify(sejmApiClient).fetchInterpellationsModifiedSince(10, expectedSince);
    }

    @Test
    void givenQueuedStateAlreadyExists_whenCollectInterpellations_thenDoesNotEnqueueDuplicateMessage() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var queuePort = new RecordingQueuePort();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            queuePort,
            repository,
            objectMapper);
        var expectedSince = LocalDateTime.of(TEST_DATE, LocalTime.MIDNIGHT);
        when(sejmApiClient.fetchInterpellationsModifiedSince(10, expectedSince))
                .thenReturn(List.of(InterpellationItem.missing(
                        77,
                        "Interpelacja testowa",
                        List.of("Ministerstwo"),
                        "2026-06-13",
                        "2026-06-13T00:00:00",
                        List.of(),
                        List.of())));
        repository.statuses.put("10:77", "QUEUED");

        var count = service.collectInterpellations(10, TEST_DATE);

        assertThat(count).isEqualTo(1);
        assertThat(queuePort.enqueued).isEmpty();
    }

        @Test
        void givenQueueEnqueueFails_whenCollectInterpellations_thenMarksRetryableEnqueueFailureState() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var queuePort = new RecordingQueuePort();
        queuePort.failWith = new RuntimeException("queue unavailable");
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            queuePort,
            repository,
            objectMapper);
        var expectedSince = LocalDateTime.of(TEST_DATE, LocalTime.MIDNIGHT);
        when(sejmApiClient.fetchInterpellationsModifiedSince(10, expectedSince))
            .thenReturn(List.of(InterpellationItem.missing(
                77,
                "Interpelacja testowa",
                List.of("Ministerstwo"),
                "2026-06-13",
                "2026-06-13T00:00:00",
                List.of(),
                List.of())));

        assertThatThrownBy(() -> service.collectInterpellations(10, TEST_DATE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to collect interpellations")
            .hasCauseInstanceOf(IllegalStateException.class)
            .cause()
            .hasMessageContaining("Failed to enqueue interpellation publish message");

        assertThat(repository.statuses.get("10:77")).isEqualTo("QUEUE_ENQUEUE_FAILED");
        }

        @Test
        void givenPreviouslyFailedEnqueueState_whenCollectInterpellations_thenEnqueuesAgainAndTransitionsToQueued() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        repository.statuses.put("10:77", "QUEUE_ENQUEUE_FAILED");
        var queuePort = new RecordingQueuePort();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            queuePort,
            repository,
            objectMapper);
        var expectedSince = LocalDateTime.of(TEST_DATE, LocalTime.MIDNIGHT);
        when(sejmApiClient.fetchInterpellationsModifiedSince(10, expectedSince))
            .thenReturn(List.of(InterpellationItem.missing(
                77,
                "Interpelacja testowa",
                List.of("Ministerstwo"),
                "2026-06-13",
                "2026-06-13T00:00:00",
                List.of(),
                List.of())));

        var count = service.collectInterpellations(10, TEST_DATE);

        assertThat(count).isEqualTo(1);
        assertThat(queuePort.enqueued).hasSize(1);
        assertThat(repository.statuses.get("10:77")).isEqualTo("QUEUED");
        }

    @Test
    void givenPrintItems_whenCollectPrints_thenCallsPrintApiAndUsesNumberAsKey() {
        var sejmApiClient = org.mockito.Mockito.mock(SejmApiClient.class);
        var repository = new RecordingRepository();
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            new RecordingQueuePort(),
            repository,
            objectMapper);
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
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            new RecordingQueuePort(),
            repository,
            objectMapper);
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
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            new RecordingQueuePort(),
            repository,
            objectMapper);
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
            public String writeValueAsString(Object value)
                    throws JsonProcessingException {
                throw new JsonProcessingException("boom") {
                };
            }
        };
        objectMapper.registerModule(new JavaTimeModule());
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            new RecordingQueuePort(),
            repository,
            objectMapper);
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
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            new RecordingQueuePort(),
            repository,
            objectMapper);
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
        var service = new SejmCollectService(
            sejmApiClient,
            repository,
            new RecordingQueuePort(),
            repository,
            objectMapper);

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

        private static final class RecordingRepository
            implements SejmDailyDigestPersistence, InterpellationPublishStatePort {

        private final List<UpsertCall> calls = new ArrayList<>();
        private final Map<String, String> statuses = new HashMap<>();
        private RuntimeException failWith;

        @Override
        public int upsertItem(LocalDate date, String dataType,
                String itemKey, String title, String itemJson) {
            if (this.failWith != null) {
                throw this.failWith;
            }
            this.calls.add(new UpsertCall(date, dataType, itemKey, title, itemJson));
            return 1;
        }

        @Override
        public List<java.util.Map<String, Object>> findByDate(LocalDate date) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public List<java.util.Map<String, Object>> findByDateAndType(LocalDate date,
                String dataType) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public int insertPublishLog(LocalDate date, String message,
                boolean success, String errorMsg) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public boolean alreadyPublishedToday(LocalDate date) {
            throw new UnsupportedOperationException("Not used by this test");
        }

        @Override
        public boolean tryCreateQueuedRecord(
                InterpellationPublishQueueMessage message,
                LocalDate collectionDate) {
            var key = key(message.termNum(), message.interpellationNum());
            var current = this.statuses.get(key);
            if (current == null || "QUEUE_ENQUEUE_FAILED".equals(current)) {
                this.statuses.put(key, "QUEUED");
                return true;
            }
            return false;
        }

        @Override
        public boolean tryClaimForPublish(InterpellationPublishQueueMessage message) {
            var key = key(message.termNum(), message.interpellationNum());
            var current = this.statuses.get(key);
            if ("QUEUED".equals(current) || "RETRY_SCHEDULED".equals(current)) {
                this.statuses.put(key, "PROCESSING");
                return true;
            }
            return false;
        }

        @Override
        public boolean isPublished(int termNum, int interpellationNum) {
            return "PUBLISHED".equals(this.statuses.get(key(termNum, interpellationNum)));
        }

        @Override
        public void markPublished(
                InterpellationPublishQueueMessage message,
                String facebookPostMessage) {
            this.statuses.put(key(message.termNum(), message.interpellationNum()), "PUBLISHED");
        }

        @Override
        public void markPublishConfirmationPending(
                InterpellationPublishQueueMessage message,
                String errorMessage,
                String facebookPostMessage) {
            this.statuses.put(key(message.termNum(), message.interpellationNum()), "PUBLISH_CONFIRMATION_PENDING");
        }

        @Override
        public void markRetryScheduled(
                InterpellationPublishQueueMessage message,
                String errorMessage) {
            this.statuses.put(key(message.termNum(), message.interpellationNum()), "RETRY_SCHEDULED");
        }

        @Override
        public void markEnqueueFailed(
                InterpellationPublishQueueMessage message,
                String errorMessage) {
            this.statuses.put(key(message.termNum(), message.interpellationNum()), "QUEUE_ENQUEUE_FAILED");
        }

        @Override
        public void markDeadLetter(
                InterpellationPublishQueueMessage message,
                String errorMessage) {
            this.statuses.put(key(message.termNum(), message.interpellationNum()), "DEAD_LETTER");
        }

        @Override
        public int getLastKnownReplyCount(int termNum, int interpellationNum) {
            return 0;
        }

        @Override
        public void updateLastKnownReplyCount(int termNum, int interpellationNum, int replyCount) {
            // not used in these tests
        }

        @Override
        public void markReplyNotificationPublished(
                int termNum, int interpellationNum, java.time.LocalDateTime publishedAt) {
            // not used in these tests
        }

        private String key(int termNum, int interpellationNum) {
            return termNum + ":" + interpellationNum;
        }
    }

    private static final class RecordingQueuePort implements InterpellationPublishQueuePort {

        private final List<QueueCall> enqueued = new ArrayList<>();
        private RuntimeException failWith;

        @Override
        public void enqueue(InterpellationPublishQueueMessage message, Duration visibilityDelay) {
            if (this.failWith != null) {
                throw this.failWith;
            }
            this.enqueued.add(new QueueCall(message, visibilityDelay));
        }

        @Override
        public void enqueueDeadLetter(InterpellationPublishQueueMessage message) {
            throw new UnsupportedOperationException("Not used by this test");
        }
    }

    private record UpsertCall(LocalDate date, String dataType, String itemKey,
            String title, String itemJson) {
    }

    private record QueueCall(InterpellationPublishQueueMessage message, Duration visibilityDelay) {
    }
}