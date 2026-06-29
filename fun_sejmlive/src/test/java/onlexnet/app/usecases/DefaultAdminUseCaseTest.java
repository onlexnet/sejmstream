package onlexnet.app.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import onlexnet.app.ports.in.AdminUseCase.TelegramCommandResult;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmApiClient.SejmPrints;
import onlexnet.app.ports.out.SejmApiClient.SejmTerm;
import onlexnet.sejmapi.FacebookPublisher;
import onlexnet.sejmapi.SejmCollectService;
import onlexnet.sejmapi.SejmDailyDigestRepository;
import onlexnet.sejmapi.SejmDigestService;

class DefaultAdminUseCaseTest {

    @Test
    void givenAuthorizedHelpCommand_whenHandled_thenReturnsHelpMessage() {
        var useCase = this.createUseCase(Optional.empty(), "1001");

        var result = useCase.handleTelegramCommand(1001L, "/help");

        assertThat(result)
            .isInstanceOfSatisfying(TelegramCommandResult.Reply.class, reply -> {
                assertThat(reply.message()).contains("Dostępne komendy");
                assertThat(reply.message()).contains("/collect");
            });
    }

    @Test
    void givenUnauthorizedChat_whenHandled_thenUnsupportedMessageWithChatIdIsReturned() {
        var useCase = this.createUseCase(Optional.empty(), "1001");

        var result = useCase.handleTelegramCommand(2002L, "/help");

        assertThat(result)
            .isInstanceOfSatisfying(TelegramCommandResult.Reply.class, reply -> {
                assertThat(reply.message())
                    .contains("chat id 2002")
                    .contains("not supported by the app settings");
            });
    }

    @Test
    void givenMissingAllowedChatId_whenHandled_thenUnsupportedMessageWithChatIdIsReturned() {
        var useCase = this.createUseCase(Optional.empty(), "");

        var result = useCase.handleTelegramCommand(1001L, "/help");

        assertThat(result)
            .isInstanceOfSatisfying(TelegramCommandResult.Reply.class, reply -> {
                assertThat(reply.message())
                    .contains("chat id 1001")
                    .contains("not supported by the app settings");
            });
    }

    @Test
    void givenDataCommand_whenHandled_thenReturnsCurrentTermSummary() {
        var sejmApiClient = mock(SejmApiClient.class);
        var sejmCollectService = mock(SejmCollectService.class);
        var sejmDigestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestRepository.class);

        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(false, LocalDate.of(2019, 1, 1), 9,
                new SejmPrints(1, LocalDateTime.of(2023, 10, 10, 10, 0), "link-9"),
                        LocalDate.of(2023, 10, 10)),
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                new SejmPrints(2, LocalDateTime.of(2023, 10, 11, 10, 0), "link-10"),
                LocalDate.of(2027, 10, 10))));

        var useCase = new DefaultAdminUseCase(
                sejmApiClient,
                sejmCollectService,
                sejmDigestService,
                repository,
                Optional.empty(),
                "1001");

        var result = useCase.handleTelegramCommand(1001L, "/data");

        assertThat(result)
            .isInstanceOfSatisfying(TelegramCommandResult.Reply.class, reply -> {
                assertThat(reply.message()).contains("Aktualna kadencja Sejmu: 10");
                assertThat(reply.message()).contains("Liczba kadencji w odpowiedzi API: 2");
            });
    }

    @Test
    void givenCollectCommand_whenHandled_thenReturnsCollectionSummary() {
        var sejmApiClient = mock(SejmApiClient.class);
        var sejmCollectService = mock(SejmCollectService.class);
        var sejmDigestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestRepository.class);

        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                new SejmPrints(2, LocalDateTime.of(2023, 10, 11, 10, 0), "link-10"),
                LocalDate.of(2027, 10, 10))));
        when(sejmCollectService.collectVotings(any(Integer.class), any(LocalDate.class))).thenReturn(3);
        when(sejmCollectService.collectCommitteeSittings(any(Integer.class), any(LocalDate.class))).thenReturn(4);
        when(sejmCollectService.collectPrints(any(Integer.class), any(LocalDate.class))).thenReturn(5);
        when(sejmCollectService.collectInterpellations(any(Integer.class), any(LocalDate.class))).thenReturn(2);
        when(sejmCollectService.collectWrittenQuestions(any(Integer.class), any(LocalDate.class))).thenReturn(1);
        when(sejmCollectService.collectBills(any(Integer.class), any(LocalDate.class))).thenReturn(6);

        var useCase = new DefaultAdminUseCase(
                sejmApiClient,
                sejmCollectService,
                sejmDigestService,
                repository,
                Optional.empty(),
                "1001");

        var result = useCase.handleTelegramCommand(1001L, "/collect");

        assertThat(result)
            .isInstanceOfSatisfying(TelegramCommandResult.Reply.class, reply -> {
                assertThat(reply.message()).contains("Zbieranie zakończone.");
                assertThat(reply.message()).contains("Łącznie: 21");
            });
    }

    @Test
    void givenPublishCommandWithoutFacebookBean_whenHandled_thenReportsDisabledPublishing() {
        var useCase = this.createUseCase(Optional.empty(), "1001");

        var result = useCase.handleTelegramCommand(1001L, "/publish");

        assertThat(result)
            .isInstanceOfSatisfying(TelegramCommandResult.Reply.class,
                reply -> assertThat(reply.message()).contains("Publikacja Facebook jest wyłączona"));
    }

    @Test
    void givenPublishCommandWithDigest_whenHandled_thenPublishesAndLogsSuccess() {
        var sejmApiClient = mock(SejmApiClient.class);
        var sejmCollectService = mock(SejmCollectService.class);
        var sejmDigestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestRepository.class);
        var facebookPublisher = mock(FacebookPublisher.class);

        when(repository.alreadyPublishedToday(any(LocalDate.class))).thenReturn(false);
        when(sejmDigestService.buildDigest(any(LocalDate.class))).thenReturn(Optional.of("digest message"));

        var useCase = new DefaultAdminUseCase(
                sejmApiClient,
                sejmCollectService,
                sejmDigestService,
                repository,
                Optional.of(facebookPublisher),
                "1001");

        var result = useCase.handleTelegramCommand(1001L, "/publish");

        verify(facebookPublisher).publish("digest message");
        verify(repository).insertPublishLog(any(LocalDate.class), anyString(), anyBoolean(), any());
        assertThat(result)
            .isInstanceOfSatisfying(TelegramCommandResult.Reply.class,
                reply -> assertThat(reply.message()).contains("Opublikowano digest na Facebooku"));
    }

    @Test
    void givenBlankText_whenHandled_thenNoReplyIsReturned() {
        var useCase = this.createUseCase(Optional.empty(), "1001");

        var result = useCase.handleTelegramCommand(1001L, "   ");

        assertThat(result).isEqualTo(TelegramCommandResult.NoReply.NO_REPLY);
    }

    private DefaultAdminUseCase createUseCase(
            Optional<FacebookPublisher> facebookPublisher,
            String allowedChatId) {
        var sejmApiClient = mock(SejmApiClient.class);
        var sejmCollectService = mock(SejmCollectService.class);
        var sejmDigestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestRepository.class);

        return new DefaultAdminUseCase(
                sejmApiClient,
                sejmCollectService,
                sejmDigestService,
                repository,
                facebookPublisher,
                allowedChatId);
    }
}