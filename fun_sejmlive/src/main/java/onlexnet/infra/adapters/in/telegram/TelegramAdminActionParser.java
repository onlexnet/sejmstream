package onlexnet.infra.adapters.in.telegram;

import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import onlexnet.app.ports.in.admin.AdminAction;

/**
 * Converts Telegram text commands into canonical admin actions.
 */
@Component
public final class TelegramAdminActionParser {

    /**
     * Parses one inbound text command.
     */
    public AdminAction parse(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return AdminAction.Noop.INSTANCE;
        }

        var token = this.normalizeCommand(text);
        return switch (token) {
            case "/help", "/start" -> AdminAction.Help.INSTANCE;
            case "/data" -> AdminAction.Data.INSTANCE;
            case "/collect" -> AdminAction.Collect.INSTANCE;
            case "/publish" -> AdminAction.Publish.INSTANCE;
            default -> new AdminAction.Unknown(token);
        };
    }

    private String normalizeCommand(String text) {
        var token = text.trim().split("\\s+", 2)[0];
        var atIndex = token.indexOf('@');
        if (atIndex > 0) {
            token = token.substring(0, atIndex);
        }
        return token.toLowerCase(Locale.ROOT);
    }
}
