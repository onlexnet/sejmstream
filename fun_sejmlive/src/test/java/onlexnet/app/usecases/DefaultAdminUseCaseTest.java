package onlexnet.app.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import onlexnet.app.ports.in.admin.AdminAction;
import onlexnet.app.ports.in.admin.AdminActor;
import onlexnet.app.ports.in.admin.AdminCommandRequest;
import onlexnet.app.ports.in.admin.AdminOutcome;
import onlexnet.app.ports.in.collect.CollectDailyDigestCommand;
import onlexnet.app.ports.in.collect.CollectDailyDigestOutcome;
import onlexnet.app.ports.in.collect.CollectDailyDigestUseCase;
import onlexnet.app.ports.in.publish.PublishDailyDigestCommand;
import onlexnet.app.ports.in.publish.PublishDailyDigestOutcome;
import onlexnet.app.ports.in.publish.PublishDailyDigestUseCase;
import onlexnet.app.ports.out.AdminAccessPolicy;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmApiClient.SejmPrints;
import onlexnet.app.ports.out.SejmApiClient.SejmTerm;

class DefaultAdminUseCaseTest {

    @Test
    void givenAuthorizedHelpAction_whenHandled_thenReturnsHelpOutcomeCode() {
        var accessPolicy = this.allowAllAccessPolicy();
        var useCase = this.createUseCase(mock(PublishDailyDigestUseCase.class), accessPolicy);

        var result = useCase.handleAdminAction(this.request(AdminAction.Help.INSTANCE, "1001"));

        assertThat(result)
            .isInstanceOfSatisfying(AdminOutcome.HelpOverview.class, reply -> {
                assertThat(reply.category()).isEqualTo(AdminOutcome.OutcomeCategory.BUSINESS);
            });
    }

    @Test
    void givenUnauthorizedActor_whenHandled_thenGenericUnauthorizedOutcomeIsReturned() {
        var accessPolicy = mock(AdminAccessPolicy.class);
        when(accessPolicy.isAllowed(any(AdminActor.class), any(AdminAction.class))).thenReturn(false);
        var useCase = this.createUseCase(mock(PublishDailyDigestUseCase.class), accessPolicy);

        var result = useCase.handleAdminAction(this.request(AdminAction.Help.INSTANCE, "2002"));

        assertThat(result)
            .isInstanceOfSatisfying(AdminOutcome.Unauthorized.class, reply -> {
                assertThat(reply.category()).isEqualTo(AdminOutcome.OutcomeCategory.BUSINESS);
            });
    }

    @Test
    void givenDataAction_whenHandled_thenReturnsCurrentTermSummaryArguments() {
        var sejmApiClient = mock(SejmApiClient.class);
        var collectDailyDigestUseCase = mock(CollectDailyDigestUseCase.class);
        var accessPolicy = this.allowAllAccessPolicy();

        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(false, LocalDate.of(2019, 1, 1), 9,
                new SejmPrints(1, LocalDateTime.of(2023, 10, 10, 10, 0), "link-9"),
                        LocalDate.of(2023, 10, 10)),
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                new SejmPrints(2, LocalDateTime.of(2023, 10, 11, 10, 0), "link-10"),
                LocalDate.of(2027, 10, 10))));

        var useCase = new DefaultAdminUseCase(
                sejmApiClient,
                collectDailyDigestUseCase,
                mock(PublishDailyDigestUseCase.class),
                accessPolicy,
                "test-version");

        var result = useCase.handleAdminAction(this.request(AdminAction.Data.INSTANCE, "1001"));

        assertThat(result)
            .isInstanceOfSatisfying(AdminOutcome.DataSummary.class, reply -> {
                assertThat(reply.termNum()).isEqualTo(10);
                assertThat(reply.termCount()).isEqualTo(2);
            });
    }

    @Test
    void givenCollectAction_whenHandled_thenReturnsCollectionSummaryArguments() {
        var sejmApiClient = mock(SejmApiClient.class);
        var collectDailyDigestUseCase = mock(CollectDailyDigestUseCase.class);
        var accessPolicy = this.allowAllAccessPolicy();
        when(collectDailyDigestUseCase.collect(any(CollectDailyDigestCommand.class)))
            .thenReturn(new CollectDailyDigestOutcome.Collected(LocalDate.now(), 10, Map.of(
                CollectDailyDigestOutcome.TYPE_VOTING, 3,
                CollectDailyDigestOutcome.TYPE_COMMITTEE_SITTING, 4,
                CollectDailyDigestOutcome.TYPE_PRINT, 5,
                CollectDailyDigestOutcome.TYPE_INTERPELLATION, 2,
                CollectDailyDigestOutcome.TYPE_WRITTEN_QUESTION, 1,
                CollectDailyDigestOutcome.TYPE_BILL, 6)));

        var useCase = new DefaultAdminUseCase(
                sejmApiClient,
            collectDailyDigestUseCase,
            mock(PublishDailyDigestUseCase.class),
                accessPolicy,
                "test-version");

        var result = useCase.handleAdminAction(this.request(AdminAction.Collect.INSTANCE, "1001"));

        assertThat(result)
            .isInstanceOfSatisfying(AdminOutcome.CollectSuccess.class, reply -> {
                assertThat(reply.total()).isEqualTo(21);
                assertThat(reply.votings()).isEqualTo(3);
            });
    }

    @Test
    void givenPublishActionWithDigest_whenHandled_thenPublishesAndLogsSuccess() {
        var sejmApiClient = mock(SejmApiClient.class);
        var collectDailyDigestUseCase = mock(CollectDailyDigestUseCase.class);
        var publishUseCase = mock(PublishDailyDigestUseCase.class);
        var accessPolicy = this.allowAllAccessPolicy();

        when(publishUseCase.publish(any(PublishDailyDigestCommand.class)))
            .thenReturn(new PublishDailyDigestOutcome.Published(LocalDate.now(), "digest message"));

        var useCase = new DefaultAdminUseCase(
                sejmApiClient,
                collectDailyDigestUseCase,
                publishUseCase,
                accessPolicy,
                "test-version");

        var result = useCase.handleAdminAction(this.request(AdminAction.Publish.INSTANCE, "1001"));

        verify(publishUseCase).publish(any(PublishDailyDigestCommand.class));
        assertThat(result)
            .isInstanceOf(AdminOutcome.PublishSuccess.class);
    }

    @Test
    void givenNoopAction_whenHandled_thenNoReplyIsReturned() {
        var accessPolicy = mock(AdminAccessPolicy.class);
        var useCase = this.createUseCase(mock(PublishDailyDigestUseCase.class), accessPolicy);

        var result = useCase.handleAdminAction(this.request(AdminAction.Noop.INSTANCE, "1001"));

        assertThat(result)
            .isInstanceOf(AdminOutcome.NoopIgnored.class);
    }

    @Test
    void givenUnknownAction_whenHandled_thenUnknownActionOutcomeIsReturned() {
        var useCase = this.createUseCase(mock(PublishDailyDigestUseCase.class), this.allowAllAccessPolicy());

        var result = useCase.handleAdminAction(this.request(new AdminAction.Unknown("/mystery"), "1001"));

        assertThat(result)
                .isInstanceOfSatisfying(AdminOutcome.UnknownAction.class, reply -> {
                    assertThat(reply.command()).isEqualTo("/mystery");
                });
    }

    private DefaultAdminUseCase createUseCase(
            PublishDailyDigestUseCase publishUseCase,
            AdminAccessPolicy accessPolicy) {
        var sejmApiClient = mock(SejmApiClient.class);
        var collectDailyDigestUseCase = mock(CollectDailyDigestUseCase.class);

        return new DefaultAdminUseCase(
                sejmApiClient,
            collectDailyDigestUseCase,
                publishUseCase,
                accessPolicy,
                "test-version");
    }

    private AdminAccessPolicy allowAllAccessPolicy() {
        var accessPolicy = mock(AdminAccessPolicy.class);
        when(accessPolicy.isAllowed(any(AdminActor.class), any(AdminAction.class))).thenReturn(true);
        return accessPolicy;
    }

    private AdminCommandRequest request(AdminAction action, String actorId) {
        return new AdminCommandRequest(
                "request-id",
                Instant.parse("2026-06-29T10:15:30Z"),
                new AdminActor.ExternalActor(actorId),
                action,
                Map.of());
    }
}