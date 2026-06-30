package onlexnet.infra.adapters.out;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import onlexnet.app.ports.in.admin.AdminAction;
import onlexnet.app.ports.in.admin.AdminActor;
import onlexnet.app.ports.out.AdminAccessPolicy;

/**
 * Property-backed access policy for external actors.
 */
@Component
public final class PropertyAdminAccessPolicy implements AdminAccessPolicy {

    private final String allowedTelegramChatId;

    public PropertyAdminAccessPolicy(@Value("${TELEGRAM_ALLOWED_CHAT_ID}") String allowedTelegramChatId) {
        var normalizedChatId = allowedTelegramChatId == null ? "" : allowedTelegramChatId.trim();
        if (normalizedChatId.isBlank()) {
            throw new IllegalArgumentException("TELEGRAM_ALLOWED_CHAT_ID must be configured and non-blank");
        }
        this.allowedTelegramChatId = normalizedChatId;
    }

    @Override
    public boolean isAllowed(AdminActor actor, AdminAction action) {
        if (actor instanceof AdminActor.ExternalActor externalActor) {
            return this.allowedTelegramChatId.equals(externalActor.externalId());
        }
        return false;
    }
}
