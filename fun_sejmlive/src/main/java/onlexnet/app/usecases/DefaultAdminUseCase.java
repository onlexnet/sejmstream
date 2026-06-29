package onlexnet.app.usecases;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import onlexnet.app.ports.in.AdminUseCase;
import onlexnet.app.ports.out.SejmApiClient;
import onlexnet.sejmapi.FacebookPublisher;
import onlexnet.sejmapi.SejmCollectService;
import onlexnet.sejmapi.SejmDailyDigestRepository;
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
    private final SejmDailyDigestRepository sejmDailyDigestRepository;
    private final Optional<FacebookPublisher> facebookPublisher;
    private final String allowedChatId;

    public DefaultAdminUseCase(
            SejmApiClient sejmApiClient,
            SejmCollectService sejmCollectService,
            SejmDigestService sejmDigestService,
            SejmDailyDigestRepository sejmDailyDigestRepository,
            Optional<FacebookPublisher> facebookPublisher,
            @Value("${TELEGRAM_ALLOWED_CHAT_ID}") String allowedChatId) {
        this.sejmApiClient = sejmApiClient;
        this.sejmCollectService = sejmCollectService;
        this.sejmDigestService = sejmDigestService;
        this.sejmDailyDigestRepository = sejmDailyDigestRepository;
        this.facebookPublisher = facebookPublisher;
        this.allowedChatId = allowedChatId;
    }

    @Override
    public TelegramCommandResult handleTelegramCommand(long chatId, String text) {
        if (!this.isChatAllowed(chatId)) {
            LOGGER.warn("Ignoring Telegram command from unauthorized chat {}", chatId);
            return TelegramCommandResult.reply(this.unsupportedChatMessage(chatId));
        }

        if (text == null || text.isBlank()) {
            return TelegramCommandResult.noReply();
        }

        var command = this.normalizeCommand(text);
        var response = switch (command) {
            case "/help", "/start" -> this.helpMessage();
            case "/data" -> this.handleData();
            case "/collect" -> this.handleCollect();
            case "/publish" -> this.handlePublish();
            default -> "Nieznana komenda: " + command + "\n\n" + this.helpMessage();
        };

        return TelegramCommandResult.reply(response);
    }

    private boolean isChatAllowed(long chatId) {
        return this.allowedChatId.equals(Long.toString(chatId));
    }

    private String unsupportedChatMessage(long chatId) {
        return "You cannot invoke commands because your chat id " + chatId
                + " is not supported by the app settings.";
    }

    private String normalizeCommand(String text) {
        var token = text.trim().split("\\s+", 2)[0];
        var atIndex = token.indexOf('@');
        if (atIndex > 0) {
            token = token.substring(0, atIndex);
        }
        return token.toLowerCase(Locale.ROOT);
    }

    private String helpMessage() {
        return "Dostępne komendy:\n"
                + "/help - lista komend\n"
                + "/data - aktualna kadencja Sejmu\n"
                + "/collect - zbierz dzisiejsze dane sejmowe\n"
                + "/publish - opublikuj dzisiejszy digest na Facebooku";
    }

    private String handleData() {
        var terms = this.sejmApiClient.fetchTerms();
        if (terms == null || terms.isEmpty()) {
            return "Brak danych o kadencjach Sejmu.";
        }

        var currentTerm = terms.stream()
                .filter(term -> term != null && term.current())
                .findFirst()
                .orElse(terms.get(0));

        var toDate = currentTerm.to() == null ? "trwa" : currentTerm.to().toString();
        return "Aktualna kadencja Sejmu: " + currentTerm.num() + "\n"
                + "Od: " + currentTerm.from() + "\n"
                + "Do: " + toDate + "\n"
                + "Liczba kadencji w odpowiedzi API: " + terms.size();
    }

    private String handleCollect() {
        try {
            var termNum = this.resolveCurrentTermNumber();
            if (termNum.isEmpty()) {
                return "Nie udało się ustalić aktualnej kadencji Sejmu.";
            }

            var date = LocalDate.now();
            var counts = new LinkedHashMap<String, Integer>();
            counts.put("Głosowania", this.sejmCollectService.collectVotings(termNum.get(), date));
            counts.put("Komisje", this.sejmCollectService.collectCommitteeSittings(termNum.get(), date));
            counts.put("Druki", this.sejmCollectService.collectPrints(termNum.get(), date));
            counts.put("Interpelacje", this.sejmCollectService.collectInterpellations(termNum.get(), date));
            counts.put("Zapytania", this.sejmCollectService.collectWrittenQuestions(termNum.get(), date));
            counts.put("Projekty", this.sejmCollectService.collectBills(termNum.get(), date));

            var total = counts.values().stream().mapToInt(Integer::intValue).sum();
            return this.formatCollectSummary(date, termNum.get(), total, counts);
        } catch (RuntimeException exception) {
            LOGGER.warn("Telegram /collect command failed", exception);
            return "Polecenie /collect nie powiodło się: " + exception.getMessage();
        }
    }

    private String formatCollectSummary(
            LocalDate date,
            int termNum,
            int total,
            Map<String, Integer> counts) {
        var result = new StringBuilder();
        result.append("Zbieranie zakończone.\n")
                .append("Data: ").append(date).append("\n")
                .append("Kadencja: ").append(termNum).append("\n")
                .append("Łącznie: ").append(total).append("\n");

        counts.forEach((key, value) -> result.append("- ").append(key).append(": ").append(value).append("\n"));
        return result.toString().trim();
    }

    private String handlePublish() {
        if (this.facebookPublisher.isEmpty()) {
            return "Publikacja Facebook jest wyłączona (brak FB_TOKEN).";
        }

        var date = LocalDate.now();
        if (this.sejmDailyDigestRepository.alreadyPublishedToday(date)) {
            return "Digest dla dnia " + date + " został już opublikowany.";
        }

        try {
            var digest = this.sejmDigestService.buildDigest(date);
            if (digest.isEmpty()) {
                return "Brak danych do publikacji dla dnia " + date + ".";
            }

            var message = digest.get();
            this.facebookPublisher.get().publish(message);
            this.sejmDailyDigestRepository.insertPublishLog(date, message, true, null);
            return "Opublikowano digest na Facebooku dla dnia " + date + ".";
        } catch (RuntimeException exception) {
            var errorMessage = exception.getMessage();
            this.tryWriteFailedPublishLog(date, errorMessage == null ? "Unknown error" : errorMessage);
            LOGGER.warn("Telegram /publish command failed", exception);
            return "Publikacja nie powiodła się: " + exception.getMessage();
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
}