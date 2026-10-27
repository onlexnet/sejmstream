package onlexnet.app.usecases;

import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import onlexnet.app.ports.in.ingest.IngestDailyDigestCommand;
import onlexnet.app.ports.in.ingest.IngestDailyDigestOutcome;
import onlexnet.app.ports.in.ingest.IngestDailyDigestUseCase;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.shared.Guards;

/**
 * Default application implementation for daily-digest ingestion.
 */
@Component
@RequiredArgsConstructor
public class DefaultIngestDailyDigestUseCase implements IngestDailyDigestUseCase {

    private final SejmApiClient sejmApiClient;
    private final SejmCollectOperations sejmCollectService;

    @Override
    public IngestDailyDigestOutcome ingest(IngestDailyDigestCommand command) {
        var date = command.date();
        var termNum = resolveCurrentTermNumber();
        if (termNum.isEmpty()) {
            return new IngestDailyDigestOutcome.TermMissing(date);
        }

        try {
            var term = termNum.get();
            var countsByType = Map.of(
                    IngestDailyDigestOutcome.TYPE_VOTING, this.sejmCollectService.collectVotings(term, date),
                    IngestDailyDigestOutcome.TYPE_COMMITTEE_SITTING,
                    this.sejmCollectService.collectCommitteeSittings(term, date),
                    IngestDailyDigestOutcome.TYPE_PRINT, this.sejmCollectService.collectPrints(term, date),
                    IngestDailyDigestOutcome.TYPE_INTERPELLATION,
                    this.sejmCollectService.collectInterpellations(term, date),
                    IngestDailyDigestOutcome.TYPE_WRITTEN_QUESTION,
                    this.sejmCollectService.collectWrittenQuestions(term, date),
                    IngestDailyDigestOutcome.TYPE_BILL, this.sejmCollectService.collectBills(term, date));
            return new IngestDailyDigestOutcome.Ingested(date, term, countsByType);
        } catch (RuntimeException exception) {
            return new IngestDailyDigestOutcome.Failed(date, exception);
        }
    }

    private java.util.Optional<Integer> resolveCurrentTermNumber() {
        var terms = this.sejmApiClient.fetchTerms();
        var safeTerms = Guards.orDefaultIfNullOrEmpty(
                terms,
                java.util.Collections.<SejmApiClient.SejmTerm>emptyList());
        if (safeTerms.isEmpty()) {
            return java.util.Optional.empty();
        }
        return safeTerms.stream()
                .filter(term -> term != null && term.current())
                .map(term -> term.num())
                .findFirst();
    }
}
