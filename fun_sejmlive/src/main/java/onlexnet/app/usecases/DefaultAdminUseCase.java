package onlexnet.app.usecases;

import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import onlexnet.app.ports.in.admin.AdminAction;
import onlexnet.app.ports.in.admin.AdminCommandRequest;
import onlexnet.app.ports.in.admin.AdminOutcome;
import onlexnet.app.ports.in.admin.AdminUseCase;
import onlexnet.app.ports.in.publish.PublishDailyDigestCommand;
import onlexnet.app.ports.in.publish.PublishDailyDigestOutcome;
import onlexnet.app.ports.in.publish.PublishDailyDigestUseCase;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.AdminAccessPolicy;
import onlexnet.sejmapi.SejmCollectService;

/**
 * Default application implementation for admin command processing.
 */
@Component
@RequiredArgsConstructor
public class DefaultAdminUseCase implements AdminUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAdminUseCase.class);

    private final SejmApiClient sejmApiClient;
    private final SejmCollectService sejmCollectService;
    private final PublishDailyDigestUseCase publishDailyDigestUseCase;
    private final AdminAccessPolicy accessPolicy;

    @Override
    public AdminOutcome handleAdminAction(AdminCommandRequest request) {
        if (request.action() instanceof AdminAction.Noop) {
            return new AdminOutcome.NoopIgnored();
        }

        if (!this.accessPolicy.isAllowed(request.actor(), request.action())) {
            LOGGER.warn("Ignoring unauthorized admin action {} from actor {}", request.action(), request.actor());
            return new AdminOutcome.Unauthorized();
        }

        return switch (request.action()) {
            case AdminAction.Noop ignored -> new AdminOutcome.NoopIgnored();
            case AdminAction.Help ignored -> new AdminOutcome.HelpOverview();
            case AdminAction.Data ignored -> this.handleData();
            case AdminAction.Collect ignored -> this.handleCollect();
            case AdminAction.Publish ignored -> this.handlePublish();
            case AdminAction.Unknown unknown -> new AdminOutcome.UnknownAction(unknown.command());
        };
    }

    private AdminOutcome handleData() {
        var terms = this.sejmApiClient.fetchTerms();
        if (terms == null || terms.isEmpty()) {
            return new AdminOutcome.DataEmpty();
        }

        var currentTerm = terms.stream()
                .filter(term -> term != null && term.current())
                .findFirst()
                .orElse(terms.get(0));

        return new AdminOutcome.DataSummary(
            currentTerm.num(),
            currentTerm.from(),
            Optional.ofNullable(currentTerm.to()),
            terms.size());
    }

    private AdminOutcome handleCollect() {
        try {
            var termNum = this.resolveCurrentTermNumber();
            if (termNum.isEmpty()) {
                return new AdminOutcome.CollectTermMissing();
            }

            var date = LocalDate.now();
            var votings = this.sejmCollectService.collectVotings(termNum.get(), date);
            var committeeSittings = this.sejmCollectService.collectCommitteeSittings(termNum.get(), date);
            var prints = this.sejmCollectService.collectPrints(termNum.get(), date);
            var interpellations = this.sejmCollectService.collectInterpellations(termNum.get(), date);
            var writtenQuestions = this.sejmCollectService.collectWrittenQuestions(termNum.get(), date);
            var bills = this.sejmCollectService.collectBills(termNum.get(), date);
            var total = votings + committeeSittings + prints + interpellations + writtenQuestions + bills;

            return new AdminOutcome.CollectSuccess(
                    date,
                    termNum.get(),
                    total,
                    votings,
                    committeeSittings,
                    prints,
                    interpellations,
                    writtenQuestions,
                    bills);
        } catch (RuntimeException exception) {
            LOGGER.warn("Admin collect action failed", exception);
            return new AdminOutcome.CollectFailure(this.safeErrorMessage(exception));
        }
    }

    private AdminOutcome handlePublish() {
        var outcome = this.publishDailyDigestUseCase
                .publish(new PublishDailyDigestCommand(LocalDate.now()));

        return switch (outcome) {
            case PublishDailyDigestOutcome.Published published -> new AdminOutcome.PublishSuccess(published.date());
            case PublishDailyDigestOutcome.SkippedAlreadyPublished skipped ->
                new AdminOutcome.PublishAlreadyDone(skipped.date());
            case PublishDailyDigestOutcome.SkippedNoDigest skipped -> new AdminOutcome.PublishNoData(skipped.date());
            case PublishDailyDigestOutcome.Failed failed -> {
                LOGGER.warn("Admin publish action failed", failed.exception());
                yield new AdminOutcome.PublishFailure(this.safeErrorMessage(failed.exception()));
            }
        };
    }

    private Optional<Integer> resolveCurrentTermNumber() {
        var terms = this.sejmApiClient.fetchTerms();
        if (terms == null || terms.isEmpty()) {
            return Optional.empty();
        }
        return terms.stream()
                .filter(term -> term != null && term.current())
                .map(term -> term.num())
                .findFirst();
    }

    private String safeErrorMessage(final RuntimeException exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        return message;
    }
}