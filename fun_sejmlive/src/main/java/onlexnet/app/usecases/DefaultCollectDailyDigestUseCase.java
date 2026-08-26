package onlexnet.app.usecases;

import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import onlexnet.app.ports.in.collect.CollectDailyDigestCommand;
import onlexnet.app.ports.in.collect.CollectDailyDigestOutcome;
import onlexnet.app.ports.in.collect.CollectDailyDigestUseCase;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmCollectOperations;
import onlexnet.shared.Guards;

/**
 * Default application implementation for daily collect processing.
 */
@Component
@RequiredArgsConstructor
public class DefaultCollectDailyDigestUseCase implements CollectDailyDigestUseCase {

    private final SejmApiClient sejmApiClient;
    private final SejmCollectOperations sejmCollectService;

    @Override
    public CollectDailyDigestOutcome collect(CollectDailyDigestCommand command) {
        var date = command.date();
        var termNum = this.resolveCurrentTermNumber();
        if (termNum.isEmpty()) {
            return new CollectDailyDigestOutcome.TermMissing(date);
        }

        try {
            var term = termNum.get();
            var countsByType = Map.of(
                    CollectDailyDigestOutcome.TYPE_VOTING, this.sejmCollectService.collectVotings(term, date),
                    CollectDailyDigestOutcome.TYPE_COMMITTEE_SITTING,
                    this.sejmCollectService.collectCommitteeSittings(term, date),
                    CollectDailyDigestOutcome.TYPE_PRINT, this.sejmCollectService.collectPrints(term, date),
                    CollectDailyDigestOutcome.TYPE_INTERPELLATION,
                    this.sejmCollectService.collectInterpellations(term, date),
                    CollectDailyDigestOutcome.TYPE_WRITTEN_QUESTION,
                    this.sejmCollectService.collectWrittenQuestions(term, date),
                    CollectDailyDigestOutcome.TYPE_BILL, this.sejmCollectService.collectBills(term, date));
            return new CollectDailyDigestOutcome.Collected(date, term, countsByType);
        } catch (RuntimeException exception) {
            return new CollectDailyDigestOutcome.Failed(date, exception);
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
