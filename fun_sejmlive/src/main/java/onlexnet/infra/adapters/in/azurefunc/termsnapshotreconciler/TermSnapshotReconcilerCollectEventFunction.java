package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import static onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotReconcilerContractOperations.TERM_SNAPSHOT_COLLECTED;

import java.io.IOException;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.EventHubTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.Log;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestratorEventV1DTO;

/**
 * Event Hub trigger that materializes and forwards collect v1 events to the term snapshot durable entity.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public final class TermSnapshotReconcilerCollectEventFunction {

    private final ObjectMapper objectMapper;
    private final JsonValidator jsonValidator;
    private final TermSnapshotCollectedEventMaterializer materializer;

    @FunctionName(SejmCollectFunctions.TERM_SNAPSHOT_COLLECT_EVENT_FUNCTION_NAME)
    public void runFromCollectEvent(
            @EventHubTrigger(
                    name = "eventPayload",
                    eventHubName = "%COLLECT_ORCHESTRATOR_EVENT_HUB_NAME%",
                    connection = "COLLECT_ORCHESTRATOR_EVENT_HUB_CONNECTION",
                    cardinality = Cardinality.ONE)
            String eventPayload,
            @DurableClientInput(name = "durableContext") DurableClientContext clientCtx,
            ExecutionContext execCtx) {

        var collectEvent = deserializeCollectEvent(eventPayload, execCtx);
        if (collectEvent == null) {
            return;
        }

        var termNum = requiredTermNum(collectEvent.getTermNum());
        var entityId = new EntityInstanceId(SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME, String.valueOf(termNum));

        try {
            var snapshotEvent = this.materializer.materialize(collectEvent);
            clientCtx.getClient().getEntities().signalEntity(
                    entityId,
                    TERM_SNAPSHOT_COLLECTED.methodName(),
                    snapshotEvent,
                    null);
            Log.info(execCtx,
                    "Term snapshot reconciliation event accepted from Event Hub for term="
                            + termNum
                            + ", instanceId="
                            + collectEvent.getOrchestrationInstanceId());
        } catch (RuntimeException exception) {
            log.error("Failed to materialize or signal term snapshot event", exception);
            throw new IllegalStateException("Failed to forward collect event to term snapshot reconciler", exception);
        }
    }

    private @Nullable CollectOrchestratorEventV1DTO deserializeCollectEvent(@Nullable String payload, ExecutionContext execCtx) {
        if (payload == null || payload.isBlank()) {
            execCtx.getLogger().warning("Ignoring blank collect orchestrator event payload from Event Hub");
            return null;
        }

        try {
            var parsed = this.objectMapper.readValue(payload, CollectOrchestratorEventV1DTO.class);
            return this.jsonValidator.validateReceived(JsonValidator.COLLECT_ORCHESTRATOR_EVENT_V1, parsed);
        } catch (IOException | IllegalArgumentException exception) {
            var message = safeErrorMessage(exception);
            execCtx.getLogger().warning("Ignoring malformed collect orchestrator event payload: " + message);
            log.debug("Malformed collect event payload ignored: {}", truncatePayload(payload), exception);
            return null;
        }
    }

    private static int requiredTermNum(@Nullable Integer termNum) {
        if (termNum == null || termNum <= 0) {
            throw new IllegalStateException("Collect event field termNum must be a positive integer");
        }
        return termNum;
    }

    private static String safeErrorMessage(Exception exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private static String truncatePayload(String payload) {
        var maxLength = 400;
        if (payload.length() <= maxLength) {
            return payload;
        }
        return payload.substring(0, maxLength) + "...";
    }
}
