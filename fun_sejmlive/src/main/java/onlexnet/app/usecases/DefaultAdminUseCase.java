package onlexnet.app.usecases;

import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import onlexnet.app.ports.in.admin.AdminAction;
import onlexnet.app.ports.in.admin.AdminCommandRequest;
import onlexnet.app.ports.in.admin.AdminOutcome;
import onlexnet.app.ports.in.admin.AdminUseCase;
import onlexnet.app.ports.in.collect.CollectDailyDigestCommand;
import onlexnet.app.ports.in.collect.CollectDailyDigestOutcome;
import onlexnet.app.ports.in.collect.CollectDailyDigestUseCase;
import onlexnet.app.ports.in.publish.PublishDailyDigestCommand;
import onlexnet.app.ports.in.publish.PublishDailyDigestOutcome;
import onlexnet.app.ports.in.publish.PublishDailyDigestUseCase;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.AdminAccessPolicy;

/**
 * Default application implementation for admin command processing.
 */
@Component
@RequiredArgsConstructor
public class DefaultAdminUseCase implements AdminUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAdminUseCase.class);

    private final SejmApiClient sejmApiClient;
    private final CollectDailyDigestUseCase collectDailyDigestUseCase;
    private final PublishDailyDigestUseCase publishDailyDigestUseCase;
    private final AdminAccessPolicy accessPolicy;

    @Value("${build.version:unknown}")
    private String buildVersion;

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
            case AdminAction.Noop _ -> new AdminOutcome.NoopIgnored();
            case AdminAction.Help _ -> new AdminOutcome.HelpOverview();
            case AdminAction.Data _ -> this.handleData();
            case AdminAction.Collect _ -> this.handleCollect();
            case AdminAction.Publish _ -> this.handlePublish();
            case AdminAction.Version _ -> new AdminOutcome.VersionInfo(this.buildVersion);
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
        var outcome = this.collectDailyDigestUseCase
                .collect(new CollectDailyDigestCommand(LocalDate.now()));

        return switch (outcome) {
            case CollectDailyDigestOutcome.TermMissing _ -> new AdminOutcome.CollectTermMissing();
            case CollectDailyDigestOutcome.Collected collected -> {
                var votings = this.countFor(collected, CollectDailyDigestOutcome.TYPE_VOTING);
                var committeeSittings = this.countFor(collected,
                    CollectDailyDigestOutcome.TYPE_COMMITTEE_SITTING);
                var prints = this.countFor(collected, CollectDailyDigestOutcome.TYPE_PRINT);
                var interpellations = this.countFor(collected,
                    CollectDailyDigestOutcome.TYPE_INTERPELLATION);
                var writtenQuestions = this.countFor(collected,
                    CollectDailyDigestOutcome.TYPE_WRITTEN_QUESTION);
                var bills = this.countFor(collected, CollectDailyDigestOutcome.TYPE_BILL);
                var total = votings + committeeSittings + prints + interpellations + writtenQuestions + bills;

                yield new AdminOutcome.CollectSuccess(
                        collected.date(),
                        collected.termNum(),
                        total,
                        votings,
                        committeeSittings,
                        prints,
                        interpellations,
                        writtenQuestions,
                        bills);
            }
            case CollectDailyDigestOutcome.Failed failed -> {
                LOGGER.warn("Admin collect action failed", failed.exception());
                yield new AdminOutcome.CollectFailure(this.safeErrorMessage(failed.exception()));
            }
        };
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

    private int countFor(
            final CollectDailyDigestOutcome.Collected collected,
            final String type) {
        return collected.countsByType().getOrDefault(type, 0);
    }

    private String safeErrorMessage(final RuntimeException exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        return message;
    }
}