package onlexnet.infra.adapters.in.telegram;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;

import lombok.RequiredArgsConstructor;
import onlexnet.app.ports.in.admin.AdminAction;
import onlexnet.app.ports.in.admin.AdminActor;
import onlexnet.app.ports.in.admin.AdminCommandRequest;
import onlexnet.app.ports.in.admin.AdminUseCase;
import onlexnet.app.ports.out.AdminAccessPolicy;
import onlexnet.app.ports.out.TelegramNotifier;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.collectcoordinator.CollectCoordinatorContractOperations;
import onlexnet.infra.adapters.in.telegram.model.TelegramUpdate;

/**
 * Azure Function webhook entrypoint for Telegram updates.
 */
@Component
@RequiredArgsConstructor
public final class TelegramBotFunctions {

    static final String HTTP_FUNCTION_NAME = "Fun_TelegramWebhook";
    static final String HTTP_FUNCTION_ROUTE = "telegram/webhook";
    private static final String COLLECT_RECOVER_COMMAND = "/collect_recover";
    private static final String COLLECT_RECOVER_SOURCE = "telegram-recovery";
    private static final EntityInstanceId COLLECT_COORDINATOR_ENTITY_ID =
            new EntityInstanceId(SejmCollectFunctions.COORDINATOR_ENTITY_NAME, SejmCollectFunctions.COORDINATOR_ENTITY_KEY);

    private final AdminUseCase adminUseCase;
    private final AdminAccessPolicy adminAccessPolicy;
    private final TelegramAdminActionParser adminActionParser;
    private final TelegramAdminOutcomePresenter outcomePresenter;
    private final TelegramNotifier telegramNotifier;
    private final ObjectMapper objectMapper;

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
            HttpRequestMessage<Optional<String>> request,
            @DurableClientInput(name = "durableContext") DurableClientContext durableClientContext,
            ExecutionContext context) {
        try {
            var payload = request.getBody().orElse("");
            if (!payload.isBlank()) {
                var update = this.objectMapper.readValue(payload, TelegramUpdate.class);
                this.handleUpdate(update, durableClientContext);
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

    private void handleUpdate(TelegramUpdate update, DurableClientContext durableClientContext) {
        if (update == null || update.message() == null || update.message().chat() == null) {
            return;
        }

        var chatId = update.message().chat().id();
        var actor = new AdminActor.ExternalActor(Long.toString(chatId));
        var commandToken = this.commandToken(update.message().text());
        if (COLLECT_RECOVER_COMMAND.equals(commandToken)) {
            this.handleCollectRecover(chatId, actor, durableClientContext);
            return;
        }

        AdminAction action = this.adminActionParser.parse(update.message().text());
        var request = new AdminCommandRequest(
                this.requestId(update),
                Instant.now(),
                actor,
                action,
                this.metadata(update));

        var outcome = this.adminUseCase.handleAdminAction(request);
        for (var message : this.outcomePresenter.present(outcome)) {
            this.telegramNotifier.sendMessage(chatId, message);
        }
    }

    private void handleCollectRecover(long chatId, AdminActor actor, DurableClientContext durableClientContext) {
        if (!this.adminAccessPolicy.isAllowed(actor, AdminAction.Collect.INSTANCE)) {
            this.telegramNotifier.sendMessage(chatId, "Brak uprawnień do wykonania poleceń administracyjnych.");
            return;
        }

        try {
            var entityClient = durableClientContext.getClient().getEntities();
            try {
            entityClient.signalEntity(
                COLLECT_COORDINATOR_ENTITY_ID,
                CollectCoordinatorContractOperations.FORCE_START_NEXT.methodName(),
                COLLECT_RECOVER_SOURCE);
            this.telegramNotifier.sendMessage(
                chatId,
                "Wysłano recovery collecta (forceStartNext). Koordynator powinien uruchomić kolejny przebieg.");
            } catch (RuntimeException forceStartException) {
            entityClient.signalEntity(
                COLLECT_COORDINATOR_ENTITY_ID,
                CollectCoordinatorContractOperations.REQUEST_COLLECT.methodName(),
                COLLECT_RECOVER_SOURCE);
            this.telegramNotifier.sendMessage(
                chatId,
                "forceStartNext nie powiódł się, wysłano fallback requestCollect: "
                    + forceStartException.getMessage());
            }
        } catch (RuntimeException exception) {
            this.telegramNotifier.sendMessage(
                    chatId,
                    "Recovery collecta nie powiódł się: " + exception.getMessage());
        }
    }

    private String commandToken(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        var token = text.trim().split("\\s+", 2)[0];
        var atIndex = token.indexOf('@');
        if (atIndex > 0) {
            token = token.substring(0, atIndex);
        }
        return token.toLowerCase(Locale.ROOT);
    }

    private String requestId(TelegramUpdate update) {
        return "telegram:" + update.updateId() + ":" + update.message().messageId();
    }

    private Map<String, String> metadata(TelegramUpdate update) {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put("updateId", Long.toString(update.updateId()));
        metadata.put("messageId", Long.toString(update.message().messageId()));
        metadata.put("chatType", Optional.ofNullable(update.message().chat().type()).orElse("unknown"));
        return Map.copyOf(metadata);
    }
}