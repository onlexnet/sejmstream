package onlexnet.infra.adapters.in.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Telegram update object sent to webhook endpoints.
 *
 * @param updateId update sequence identifier
 * @param message  optional message payload
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        @JsonProperty("update_id") long updateId,
        @JsonProperty("message") TelegramMessage message) {
}
