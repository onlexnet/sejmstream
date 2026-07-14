package onlexnet.infra.adapters.in.telegram;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import onlexnet.app.ports.in.admin.AdminOutcome;

/**
 * Renders channel-agnostic admin outcomes into Telegram user-facing messages.
 */
@Component
public final class TelegramAdminOutcomePresenter {

    private static final int TELEGRAM_MESSAGE_LIMIT = 3900;

    /**
     * Converts one outcome to zero or more outbound Telegram messages.
     */
    public List<String> present(AdminOutcome outcome) {
        return switch (outcome) {
            case AdminOutcome.NoReply ignored -> List.of();
            case AdminOutcome.ImmediateReply immediate -> this.chunk(this.renderImmediate(immediate));
            case AdminOutcome.DeferredReply deferred -> this.chunk(this.renderDeferred(deferred));
        };
    }

    private String renderImmediate(AdminOutcome.ImmediateReply outcome) {
        return switch (outcome) {
            case AdminOutcome.Unauthorized ignored -> "Brak uprawnień do wykonania poleceń administracyjnych.";
            case AdminOutcome.HelpOverview ignored -> this.helpText();
            case AdminOutcome.DataEmpty ignored -> "Brak danych o kadencjach Sejmu.";
            case AdminOutcome.DataSummary dataSummary -> this.renderDataSummary(dataSummary);
            case AdminOutcome.CollectTermMissing ignored -> "Nie udało się ustalić aktualnej kadencji Sejmu.";
            case AdminOutcome.CollectSuccess collectSuccess -> this.renderCollectSummary(collectSuccess);
            case AdminOutcome.CollectFailure collectFailure -> "Polecenie /collect nie powiodło się: " + collectFailure.reason();
            case AdminOutcome.PublishAlreadyDone publishAlreadyDone -> "Digest dla dnia "
                    + publishAlreadyDone.date()
                    + " został już opublikowany.";
            case AdminOutcome.PublishNoData publishNoData -> "Brak danych do publikacji dla dnia "
                    + publishNoData.date()
                    + ".";
            case AdminOutcome.PublishSuccess publishSuccess -> "Opublikowano digest na Facebooku dla dnia "
                    + publishSuccess.date()
                    + ".";
            case AdminOutcome.PublishFailure publishFailure -> "Publikacja nie powiodła się: " + publishFailure.reason();
            case AdminOutcome.VersionInfo versionInfo -> "Wersja: " + versionInfo.buildVersion();
            case AdminOutcome.UnknownAction unknownAction -> "Nieznana komenda: " + unknownAction.command() + "\n\n"
                    + this.helpText();
        };
    }

    private String helpText() {
        return "Dostępne komendy:\n"
                + "/help - lista komend\n"
                + "/data - aktualna kadencja Sejmu\n"
                + "/collect - zbierz dzisiejsze dane sejmowe\n"
                + "/publish - opublikuj dzisiejszy digest na Facebooku\n"
                + "/version - numer builda wrzuconego na produkcję";
    }

    private String renderDeferred(AdminOutcome.DeferredReply outcome) {
        return switch (outcome) {
            case AdminOutcome.ActionDeferred actionDeferred -> "Przetwarzanie zostało zaplanowane. Id sprawy: "
                    + actionDeferred.correlationId()
                    + ".";
        };
    }

    private String renderDataSummary(AdminOutcome.DataSummary dataSummary) {
        var toDate = dataSummary.to()
                .map(Object::toString)
                .orElse("trwa");
        return "Aktualna kadencja Sejmu: " + dataSummary.termNum() + "\n"
                + "Od: " + dataSummary.from() + "\n"
                + "Do: " + toDate + "\n"
                + "Liczba kadencji w odpowiedzi API: " + dataSummary.termCount();
    }

    private String renderCollectSummary(AdminOutcome.CollectSuccess collectSuccess) {
        return "Zbieranie zakończone.\n"
                + "Data: " + collectSuccess.date() + "\n"
                + "Kadencja: " + collectSuccess.termNum() + "\n"
                + "Łącznie: " + collectSuccess.total() + "\n"
                + "- Głosowania: " + collectSuccess.votings() + "\n"
                + "- Komisje: " + collectSuccess.committeeSittings() + "\n"
                + "- Druki: " + collectSuccess.prints() + "\n"
                + "- Interpelacje: " + collectSuccess.interpellations() + "\n"
                + "- Zapytania: " + collectSuccess.writtenQuestions() + "\n"
                + "- Projekty: " + collectSuccess.bills();
    }

    private List<String> chunk(String message) {
        if (message.isBlank()) {
            return List.of();
        }

        if (message.length() <= TELEGRAM_MESSAGE_LIMIT) {
            return List.of(message);
        }

        var chunks = new ArrayList<String>();
        var start = 0;
        while (start < message.length()) {
            var end = Math.min(start + TELEGRAM_MESSAGE_LIMIT, message.length());
            if (end < message.length()) {
                var lastBreak = message.lastIndexOf('\n', end);
                if (lastBreak > start + 32) {
                    end = lastBreak;
                }
            }
            chunks.add(message.substring(start, end).trim());
            start = end;
            while (start < message.length() && message.charAt(start) == '\n') {
                start++;
            }
        }

        return chunks;
    }
}
