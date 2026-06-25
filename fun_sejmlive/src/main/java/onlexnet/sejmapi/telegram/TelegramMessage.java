package onlexnet.sejmapi.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telegram message payload used for command handling.
 *
 * @param messageId telegram message identifier
 * @param chat      origin chat
 * @param text      plain-text message content
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
        @JsonProperty("message_id") long messageId,
        @JsonProperty("chat") TelegramChat chat,
        @JsonProperty("text") String text) {
}
