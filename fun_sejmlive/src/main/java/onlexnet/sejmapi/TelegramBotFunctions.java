package onlexnet.sejmapi;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import onlexnet.sejmapi.telegram.TelegramBotService;
import onlexnet.sejmapi.telegram.TelegramUpdate;

/**
 * Azure Function webhook entrypoint for Telegram updates.
 */
@Component
@ConditionalOnProperty("TELEGRAM_BOT_TOKEN")
public final class TelegramBotFunctions {

    static final String HTTP_FUNCTION_NAME = "Fun_TelegramWebhook";
    static final String HTTP_FUNCTION_ROUTE = "telegram/webhook";

    private final TelegramBotService telegramBotService;
    private final ObjectMapper objectMapper;

    public TelegramBotFunctions(final TelegramBotService telegramBotService,
            final ObjectMapper objectMapper) {
        this.telegramBotService = telegramBotService;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives Telegram updates and delegates command handling.
     * Always acknowledges with HTTP 200 to satisfy Telegram webhook expectations.
     *
     * @param request incoming webhook request
     * @param context Azure Functions execution context
     * @return HTTP 200 response
     */
    @FunctionName(HTTP_FUNCTION_NAME)
    public HttpResponseMessage telegramWebhook(
            @HttpTrigger(name = "request", methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS,
                    route = HTTP_FUNCTION_ROUTE)
            final HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        try {
            var payload = request.getBody().orElse("");
            if (!payload.isBlank()) {
                var update = this.objectMapper.readValue(payload, TelegramUpdate.class);
                this.telegramBotService.handleUpdate(update);
            }
        } catch (RuntimeException runtimeException) {
            context.getLogger().warning("Telegram webhook handling failed: " + runtimeException.getMessage());
        } catch (Exception exception) {
            context.getLogger().warning("Telegram webhook payload parsing failed: " + exception.getMessage());
        }

        return request.createResponseBuilder(HttpStatus.OK)
                .body("OK")
                .build();
    }
}
