package onlexnet.app.ports.in.admin;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable request for handling one canonical admin action.
 */
public record AdminCommandRequest(
        String requestId,
        Instant requestedAt,
        AdminActor actor,
        AdminAction action,
        Map<String, String> metadata) {

    public AdminCommandRequest {
        metadata = Map.copyOf(metadata);
    }
}
