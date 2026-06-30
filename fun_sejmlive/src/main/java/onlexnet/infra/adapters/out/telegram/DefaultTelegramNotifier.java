package onlexnet.infra.adapters.out.telegram;

import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import onlexnet.app.ports.out.TelegramNotifier;

/**
 * Telegram notifier implemented against the Bot API using Spring RestClient.
 */
public final class DefaultTelegramNotifier implements TelegramNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTelegramNotifier.class);
    private static final int TELEGRAM_MAX_TEXT_LENGTH = 4096;

    private final RestClient restClient;

    /**
     * Creates a notifier using a bot token.
     *
     * @param token Telegram bot token
     */
    public DefaultTelegramNotifier(final String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Telegram bot token is not configured");
        }
        this.restClient = RestClient.builder()
                .baseUrl("https://api.telegram.org/bot" + token)
                .build();
    }

    @Override
    public void sendMessage(final long chatId, final String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        var payload = Map.of(
                "chat_id", chatId,
                "text", trimToTelegramLimit(text));

        try {
            this.restClient.post()
                    .uri("/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to send Telegram message to chat {}", chatId, exception);
            throw exception;
        }
    }

    private String trimToTelegramLimit(final String text) {
        var normalized = Objects.requireNonNull(text, "text must not be null");
        if (normalized.length() <= TELEGRAM_MAX_TEXT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, TELEGRAM_MAX_TEXT_LENGTH - 1) + "…";
    }
}
