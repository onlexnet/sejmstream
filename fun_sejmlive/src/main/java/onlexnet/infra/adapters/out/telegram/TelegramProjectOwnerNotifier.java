package onlexnet.infra.adapters.out.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import onlexnet.app.ports.out.ProjectOwnerNotifier;
import onlexnet.app.ports.out.TelegramNotifier;

/**
 * Sends owner-level operational alerts through Telegram.
 */
@Component
public final class TelegramProjectOwnerNotifier implements ProjectOwnerNotifier {

    private final TelegramNotifier telegramNotifier;
    private final long ownerChatId;

    public TelegramProjectOwnerNotifier(
            TelegramNotifier telegramNotifier,
            @Value("${TELEGRAM_ALLOWED_CHAT_ID}") String ownerChatId) {
        this.telegramNotifier = telegramNotifier;
        this.ownerChatId = this.parseOwnerChatId(ownerChatId);
    }

    @Override
    public void notifyOwner(String message) {
        this.telegramNotifier.sendMessage(this.ownerChatId, message);
    }

    private long parseOwnerChatId(String rawChatId) {
        var normalized = rawChatId == null ? "" : rawChatId.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("TELEGRAM_ALLOWED_CHAT_ID must be configured and non-blank");
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("TELEGRAM_ALLOWED_CHAT_ID must be numeric", exception);
        }
    }
}
