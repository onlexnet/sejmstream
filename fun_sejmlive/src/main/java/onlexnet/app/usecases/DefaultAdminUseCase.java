package onlexnet.app.usecases;

import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import onlexnet.app.ports.in.AdminUseCase;
import onlexnet.app.ports.in.admin.AdminAction;
import onlexnet.app.ports.in.admin.AdminCommandRequest;
import onlexnet.app.ports.in.admin.AdminOutcome;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.AdminAccessPolicy;
import onlexnet.app.ports.out.FacebookPublisher;
import onlexnet.app.ports.out.SejmDailyDigestPersistence;
import onlexnet.sejmapi.SejmCollectService;
import onlexnet.sejmapi.SejmDigestService;

/**
 * Default application implementation for admin command processing.
 */
@Component
public class DefaultAdminUseCase implements AdminUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAdminUseCase.class);

    private final SejmApiClient sejmApiClient;
    private final SejmCollectService sejmCollectService;
    private final SejmDigestService sejmDigestService;
    private final SejmDailyDigestPersistence sejmDailyDigestRepository;
    private final Optional<FacebookPublisher> facebookPublisher;
    private final AdminAccessPolicy accessPolicy;

    public DefaultAdminUseCase(
            SejmApiClient sejmApiClient,
            SejmCollectService sejmCollectService,
            SejmDigestService sejmDigestService,
            SejmDailyDigestPersistence sejmDailyDigestRepository,
            Optional<FacebookPublisher> facebookPublisher,
            AdminAccessPolicy accessPolicy) {
        this.sejmApiClient = sejmApiClient;
        this.sejmCollectService = sejmCollectService;
        this.sejmDigestService = sejmDigestService;
        this.sejmDailyDigestRepository = sejmDailyDigestRepository;
        this.facebookPublisher = facebookPublisher;
        this.accessPolicy = accessPolicy;
    }

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
        if (this.facebookPublisher.isEmpty()) {
            return new AdminOutcome.PublishDisabled();
        }

        var date = LocalDate.now();
        if (this.sejmDailyDigestRepository.alreadyPublishedToday(date)) {
            return new AdminOutcome.PublishAlreadyDone(date);
        }

        try {
            var digest = this.sejmDigestService.buildDigest(date);
            if (digest.isEmpty()) {
                return new AdminOutcome.PublishNoData(date);
            }

            var message = digest.get();
            this.facebookPublisher.get().publish(message);
            this.sejmDailyDigestRepository.insertPublishLog(date, message, true, null);
            return new AdminOutcome.PublishSuccess(date);
        } catch (RuntimeException exception) {
            var errorMessage = exception.getMessage();
            this.tryWriteFailedPublishLog(date, errorMessage == null ? "Unknown error" : errorMessage);
            LOGGER.warn("Admin publish action failed", exception);
            return new AdminOutcome.PublishFailure(this.safeErrorMessage(exception));
        }
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

    private void tryWriteFailedPublishLog(LocalDate date, String errorMessage) {
        try {
            this.sejmDailyDigestRepository.insertPublishLog(date, null, false, errorMessage);
        } catch (RuntimeException logException) {
            LOGGER.warn("Failed to write publish failure log", logException);
        }
    }

    private String safeErrorMessage(RuntimeException exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        return message;
    }
}