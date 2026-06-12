package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmApiClient.BillItem;
import onlexnet.app.ports.out.SejmApiClient.CommitteeSittingItem;
import onlexnet.app.ports.out.SejmApiClient.InterpellationItem;
import onlexnet.app.ports.out.SejmApiClient.PrintItem;
import onlexnet.app.ports.out.SejmApiClient.VotingItem;
import onlexnet.app.ports.out.SejmApiClient.WrittenQuestionItem;

class SejmCollectServiceTest {

    @Test
    void collectMethodsRouteToTypedClientAndUseRequiredDigestKeys() {
        SejmApiClient sejmApiClient = mock(SejmApiClient.class);
                RecordingRepository repository = new RecordingRepository();
        SejmCollectService service = new SejmCollectService(sejmApiClient, repository,
                new ObjectMapper().findAndRegisterModules());

        int termNum = 10;
        LocalDate date = LocalDate.of(2026, 6, 12);

        VotingItem voting = new VotingItem(LocalDateTime.of(2026, 6, 12, 9, 0), 3, 17, "Vote topic", 1, 2, 3, 6, 0);
        CommitteeSittingItem committee = new CommitteeSittingItem("ABC", date, 7, "Agenda item", "DONE", "101");
        PrintItem print = new PrintItem("123", "Print title", LocalDateTime.of(2026, 6, 12, 10, 0), "2026-06-12");
        InterpellationItem interpellation = new InterpellationItem(555, "Interpellation title", List.of("PM"), "2026-06-11",
                "2026-06-12");
        WrittenQuestionItem question = new WrittenQuestionItem(777, "Question title", List.of("Minister"), "2026-06-10",
                "2026-06-12");
        BillItem bill = new BillItem("UC1", "Bill title", "2026-06-01", "GOV", "SUBMITTED");

        when(sejmApiClient.fetchVotingsForDate(termNum, date)).thenReturn(List.of(voting));
        when(sejmApiClient.fetchCommitteeSittingsForDate(termNum, date)).thenReturn(List.of(committee));
        when(sejmApiClient.fetchPrintsModifiedSince(termNum, date)).thenReturn(List.of(print));
        when(sejmApiClient.fetchInterpellationsModifiedSince(termNum, date.atStartOfDay()))
                .thenReturn(List.of(interpellation));
        when(sejmApiClient.fetchWrittenQuestionsModifiedSince(termNum, date.atStartOfDay()))
                .thenReturn(List.of(question));
        when(sejmApiClient.fetchBillsReceivedSince(termNum, date)).thenReturn(List.of(bill));

        assertThat(service.collectVotings(termNum, date)).isEqualTo(1);
        assertThat(service.collectCommitteeSittings(termNum, date)).isEqualTo(1);
        assertThat(service.collectPrints(termNum, date)).isEqualTo(1);
        assertThat(service.collectInterpellations(termNum, date)).isEqualTo(1);
        assertThat(service.collectWrittenQuestions(termNum, date)).isEqualTo(1);
        assertThat(service.collectBills(termNum, date)).isEqualTo(1);

        verify(sejmApiClient).fetchVotingsForDate(termNum, date);
        verify(sejmApiClient).fetchCommitteeSittingsForDate(termNum, date);
        verify(sejmApiClient).fetchPrintsModifiedSince(termNum, date);
        verify(sejmApiClient).fetchInterpellationsModifiedSince(termNum, date.atStartOfDay());
        verify(sejmApiClient).fetchWrittenQuestionsModifiedSince(termNum, date.atStartOfDay());
        verify(sejmApiClient).fetchBillsReceivedSince(termNum, date);

        assertThat(repository.calls)
                .extracting(call -> call.dataType + ":" + call.itemKey + ":" + call.itemTitle)
                .containsExactly(
                        "VOTING:3/17:Vote topic",
                        "COMMITTEE_SITTING:ABC/7:Agenda item",
                        "PRINT:123:Print title",
                        "INTERPELLATION:555:Interpellation title",
                        "WRITTEN_QUESTION:777:Question title",
                        "BILL:UC1:Bill title");
    }

    @Test
    void collectCommitteeSittingsFailsWhenRequiredKeyFieldIsMissing() {
        SejmApiClient sejmApiClient = mock(SejmApiClient.class);
                RecordingRepository repository = new RecordingRepository();
        SejmCollectService service = new SejmCollectService(sejmApiClient, repository,
                new ObjectMapper().findAndRegisterModules());

        int termNum = 10;
        LocalDate date = LocalDate.of(2026, 6, 12);

        when(sejmApiClient.fetchCommitteeSittingsForDate(termNum, date))
                .thenReturn(List.of(new CommitteeSittingItem("  ", date, 7, "Agenda", "DONE", "101")));

        assertThatThrownBy(() -> service.collectCommitteeSittings(termNum, date))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("code");
    }

        private static final class RecordingRepository extends SejmDailyDigestRepository {
                private final List<UpsertCall> calls = new ArrayList<>();

                private RecordingRepository() {
                        super(new JdbcTemplate());
                }

                @Override
                public int upsertItem(final LocalDate collectionDate,
                                final String dataType,
                                final String itemKey,
                                final String itemTitle,
                                final String payloadJson) {
                        this.calls.add(new UpsertCall(collectionDate, dataType, itemKey, itemTitle, payloadJson));
                        return 1;
                }
        }

        private record UpsertCall(
                        LocalDate collectionDate,
                        String dataType,
                        String itemKey,
                        String itemTitle,
                        String payloadJson) {
        }
}
