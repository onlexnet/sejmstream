package onlexnet.sejmapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.app.ports.out.SejmApiClient.SejmPrints;
import onlexnet.app.ports.out.SejmApiClient.SejmTerm;
import onlexnet.sejmapi.telegram.TelegramBotService;
import onlexnet.sejmapi.telegram.TelegramChat;
import onlexnet.sejmapi.telegram.TelegramMessage;
import onlexnet.sejmapi.telegram.TelegramNotifier;
import onlexnet.sejmapi.telegram.TelegramUpdate;

class TelegramBotServiceTest {

    @Test
    void givenAuthorizedHelpCommand_whenHandled_thenSendsHelpMessage() {
        var telegramNotifier = mock(TelegramNotifier.class);
        var service = this.createService(telegramNotifier, Optional.empty(), "1001");

        service.handleUpdate(this.update(1001L, "/help"));

        var messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier).sendMessage(org.mockito.ArgumentMatchers.eq(1001L), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Dostępne komendy");
        assertThat(messageCaptor.getValue()).contains("/collect");
    }

    @Test
    void givenUnauthorizedChat_whenHandled_thenUnsupportedMessageWithChatIdIsSent() {
        var telegramNotifier = mock(TelegramNotifier.class);
        var service = this.createService(telegramNotifier, Optional.empty(), "1001");

        service.handleUpdate(this.update(2002L, "/help"));

        var messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier).sendMessage(org.mockito.ArgumentMatchers.eq(2002L), messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .contains("chat id 2002")
                .contains("not supported by the app settings");
    }

    @Test
    void givenMissingAllowedChatId_whenHandled_thenUnsupportedMessageWithChatIdIsSent() {
        var telegramNotifier = mock(TelegramNotifier.class);
        var service = this.createService(telegramNotifier, Optional.empty(), "");

        service.handleUpdate(this.update(1001L, "/help"));

        var messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier).sendMessage(org.mockito.ArgumentMatchers.eq(1001L), messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .contains("chat id 1001")
                .contains("not supported by the app settings");
    }

    @Test
    void givenDataCommand_whenHandled_thenReturnsCurrentTermSummary() {
        var telegramNotifier = mock(TelegramNotifier.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var sejmCollectService = mock(SejmCollectService.class);
        var sejmDigestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestRepository.class);

        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(false, LocalDate.of(2019, 1, 1), 9,
                        new SejmPrints(1, null, "link-9"),
                        LocalDate.of(2023, 10, 10)),
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                        new SejmPrints(2, null, "link-10"),
                        null)));

        var service = new TelegramBotService(
                telegramNotifier,
                sejmApiClient,
                sejmCollectService,
                sejmDigestService,
                repository,
                Optional.empty(),
                "1001");

        service.handleUpdate(this.update(1001L, "/data"));

        var messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier).sendMessage(org.mockito.ArgumentMatchers.eq(1001L), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Aktualna kadencja Sejmu: 10");
        assertThat(messageCaptor.getValue()).contains("Liczba kadencji w odpowiedzi API: 2");
    }

    @Test
    void givenCollectCommand_whenHandled_thenReturnsCollectionSummary() {
        var telegramNotifier = mock(TelegramNotifier.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var sejmCollectService = mock(SejmCollectService.class);
        var sejmDigestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestRepository.class);

        when(sejmApiClient.fetchTerms()).thenReturn(List.of(
                new SejmTerm(true, LocalDate.of(2023, 10, 11), 10,
                        new SejmPrints(2, null, "link-10"),
                        null)));
        when(sejmCollectService.collectVotings(any(Integer.class), any(LocalDate.class))).thenReturn(3);
        when(sejmCollectService.collectCommitteeSittings(any(Integer.class), any(LocalDate.class))).thenReturn(4);
        when(sejmCollectService.collectPrints(any(Integer.class), any(LocalDate.class))).thenReturn(5);
        when(sejmCollectService.collectInterpellations(any(Integer.class), any(LocalDate.class))).thenReturn(2);
        when(sejmCollectService.collectWrittenQuestions(any(Integer.class), any(LocalDate.class))).thenReturn(1);
        when(sejmCollectService.collectBills(any(Integer.class), any(LocalDate.class))).thenReturn(6);

        var service = new TelegramBotService(
                telegramNotifier,
                sejmApiClient,
                sejmCollectService,
                sejmDigestService,
                repository,
                Optional.empty(),
                "1001");

        service.handleUpdate(this.update(1001L, "/collect"));

        var messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier).sendMessage(org.mockito.ArgumentMatchers.eq(1001L), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Zbieranie zakończone.");
        assertThat(messageCaptor.getValue()).contains("Łącznie: 21");
    }

    @Test
    void givenPublishCommandWithoutFacebookBean_whenHandled_thenReportsDisabledPublishing() {
        var telegramNotifier = mock(TelegramNotifier.class);
        var service = this.createService(telegramNotifier, Optional.empty(), "1001");

        service.handleUpdate(this.update(1001L, "/publish"));

        var messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier).sendMessage(org.mockito.ArgumentMatchers.eq(1001L), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Publikacja Facebook jest wyłączona");
    }

    @Test
    void givenPublishCommandWithDigest_whenHandled_thenPublishesAndLogsSuccess() {
        var telegramNotifier = mock(TelegramNotifier.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var sejmCollectService = mock(SejmCollectService.class);
        var sejmDigestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestRepository.class);
        var facebookPublisher = mock(FacebookPublisher.class);

        when(repository.alreadyPublishedToday(any(LocalDate.class))).thenReturn(false);
        when(sejmDigestService.buildDigest(any(LocalDate.class))).thenReturn(Optional.of("digest message"));

        var service = new TelegramBotService(
                telegramNotifier,
                sejmApiClient,
                sejmCollectService,
                sejmDigestService,
                repository,
                Optional.of(facebookPublisher),
                "1001");

        service.handleUpdate(this.update(1001L, "/publish"));

        verify(facebookPublisher).publish("digest message");
        verify(repository).insertPublishLog(any(LocalDate.class), anyString(), anyBoolean(), any());

        var messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier).sendMessage(org.mockito.ArgumentMatchers.eq(1001L), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Opublikowano digest na Facebooku");
    }

    private TelegramBotService createService(
            final TelegramNotifier telegramNotifier,
            final Optional<FacebookPublisher> facebookPublisher,
            final String allowedChatId) {
        var sejmApiClient = mock(SejmApiClient.class);
        var sejmCollectService = mock(SejmCollectService.class);
        var sejmDigestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestRepository.class);

        return new TelegramBotService(
                telegramNotifier,
                sejmApiClient,
                sejmCollectService,
                sejmDigestService,
                repository,
                facebookPublisher,
                allowedChatId);
    }

    private TelegramUpdate update(final long chatId, final String text) {
        return new TelegramUpdate(
                1L,
                new TelegramMessage(
                        1L,
                        new TelegramChat(chatId, "private"),
                        text));
    }
}
