package onlexnet.app.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import onlexnet.app.ports.in.collect.CollectDailyDigestCommand;
import onlexnet.app.ports.in.collect.CollectDailyDigestOutcome;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmApiClient.SejmPrints;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.app.ports.out.SejmApiClient.SejmTerm;

class DefaultCollectDailyDigestUseCaseTest {

    @Test
    void givenCurrentTermAndCollectData_whenCollectCalled_thenReturnsCollectedOutcome() {
        var date = LocalDate.of(2026, 7, 1);
        var sejmApiClient = mock(SejmApiClient.class);
        var sejmCollectService = mock(SejmCollectOperations.class);

        when(sejmApiClient.fetchTerms()).thenReturn(List.of(currentTerm(10)));
        when(sejmCollectService.collectVotings(10, date)).thenReturn(3);
        when(sejmCollectService.collectCommitteeSittings(10, date)).thenReturn(4);
        when(sejmCollectService.collectPrints(10, date)).thenReturn(5);
        when(sejmCollectService.collectInterpellations(10, date)).thenReturn(2);
        when(sejmCollectService.collectWrittenQuestions(10, date)).thenReturn(1);
        when(sejmCollectService.collectBills(10, date)).thenReturn(6);

        var useCase = new DefaultCollectDailyDigestUseCase(sejmApiClient, sejmCollectService);

        var outcome = useCase.collect(new CollectDailyDigestCommand(date));

        assertThat(outcome)
                .isInstanceOfSatisfying(CollectDailyDigestOutcome.Collected.class, collected -> {
                    assertThat(collected.date()).isEqualTo(date);
                    assertThat(collected.termNum()).isEqualTo(10);
                    assertThat(collected.countsByType()).containsEntry(CollectDailyDigestOutcome.TYPE_VOTING, 3);
                    assertThat(collected.countsByType()).containsEntry(CollectDailyDigestOutcome.TYPE_COMMITTEE_SITTING, 4);
                    assertThat(collected.countsByType()).containsEntry(CollectDailyDigestOutcome.TYPE_PRINT, 5);
                    assertThat(collected.countsByType()).containsEntry(CollectDailyDigestOutcome.TYPE_INTERPELLATION, 2);
                    assertThat(collected.countsByType())
                            .containsEntry(CollectDailyDigestOutcome.TYPE_WRITTEN_QUESTION, 1);
                    assertThat(collected.countsByType()).containsEntry(CollectDailyDigestOutcome.TYPE_BILL, 6);
                });
    }

    @Test
    void givenMissingCurrentTerm_whenCollectCalled_thenReturnsTermMissingOutcome() {
        var date = LocalDate.of(2026, 7, 1);
        var sejmApiClient = mock(SejmApiClient.class);
        var sejmCollectService = mock(SejmCollectOperations.class);

        when(sejmApiClient.fetchTerms()).thenReturn(List.of(nonCurrentTerm(9)));

        var useCase = new DefaultCollectDailyDigestUseCase(sejmApiClient, sejmCollectService);

        var outcome = useCase.collect(new CollectDailyDigestCommand(date));

        assertThat(outcome)
                .isInstanceOfSatisfying(CollectDailyDigestOutcome.TermMissing.class,
                        termMissing -> assertThat(termMissing.date()).isEqualTo(date));
    }

    @Test
    void givenCollectFailure_whenCollectCalled_thenReturnsFailedOutcome() {
        var date = LocalDate.of(2026, 7, 1);
        var sejmApiClient = mock(SejmApiClient.class);
        var sejmCollectService = mock(SejmCollectOperations.class);

        when(sejmApiClient.fetchTerms()).thenReturn(List.of(currentTerm(10)));
        when(sejmCollectService.collectVotings(eq(10), eq(date))).thenThrow(new IllegalStateException("boom"));

        var useCase = new DefaultCollectDailyDigestUseCase(sejmApiClient, sejmCollectService);

        var outcome = useCase.collect(new CollectDailyDigestCommand(date));

        assertThat(outcome)
                .isInstanceOfSatisfying(CollectDailyDigestOutcome.Failed.class,
                        failed -> assertThat(failed.exception()).isInstanceOf(IllegalStateException.class));
    }

    private SejmTerm currentTerm(int num) {
        return new SejmTerm(true, LocalDate.of(2023, 10, 11), num,
                new SejmPrints(2, LocalDateTime.of(2023, 10, 11, 10, 0), "link"),
                LocalDate.of(2027, 10, 10));
    }

    private SejmTerm nonCurrentTerm(int num) {
        return new SejmTerm(false, LocalDate.of(2019, 1, 1), num,
                new SejmPrints(1, LocalDateTime.of(2019, 1, 1, 10, 0), "link"),
                LocalDate.of(2023, 10, 10));
    }
}
