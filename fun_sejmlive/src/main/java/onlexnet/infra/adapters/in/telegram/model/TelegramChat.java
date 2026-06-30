package onlexnet.infra.adapters.in.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telegram chat metadata extracted from an update message.
 *
 * @param id   unique chat identifier
 * @param type chat type, for example private or group
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramChat(
        @JsonProperty("id") long id,
        @JsonProperty("type") String type) {
}
