package onlexnet.infra.adapters.in.azurefunc;

import static onlexnet.infra.adapters.in.azurefunc.collectcoordinator.CollectCoordinatorContractOperations.DISPATCH;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;

import lombok.extern.slf4j.Slf4j;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorRequestCollectCommandDTO;

/**
 * Shared implementation for timer and HTTP collect Azure Functions.
 */
@Component
@Slf4j
public class SejmCollectFunctionSupport {

    private static final EntityInstanceId COLLECT_COORDINATOR_ENTITY_ID =
            new EntityInstanceId(SejmCollectFunctions.COORDINATOR_ENTITY_NAME, SejmCollectFunctions.COORDINATOR_ENTITY_KEY);

    public void runTimer(String timerInfo, DurableClientContext clientCtx, ExecutionContext execCtx) {

        try {
            var instanceId = enqueueCollectRequest(clientCtx, "timer");
            Log.info(execCtx, "Collect request accepted from timer, instanceId=" + instanceId);
            log.debug("Collect request from timer accepted, instanceId={}", instanceId);
        } catch (Exception e) {
            log.error("Failed to enqueue collect request", e);
            execCtx.getLogger().severe("Error enqueueing collect request: " + e.getMessage());
            throw new IllegalStateException("Failed to enqueue collection request", e);
        }
    }

    public HttpResponseMessage httpStart(
            HttpRequestMessage<Optional<String>> request,
            DurableClientContext clientCtx,
            ExecutionContext execCtx) {

        try {
            var instanceId = enqueueCollectRequest(clientCtx, "http");
            Log.info(execCtx, "Manual collect request accepted, instanceId=" + instanceId);
            log.debug("Manual collect request accepted, instanceId={}", instanceId);
            return request.createResponseBuilder(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "accepted", true,
                            "coordinatorEntityId", instanceId,
                            "message", "Collect request was enqueued for serialized processing"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to enqueue collect request via HTTP", e);
            execCtx.getLogger().severe("Failed to enqueue collect request via HTTP: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to enqueue collection request: " + e.getMessage())
                    .build();
        }
    }

    private String enqueueCollectRequest(DurableClientContext clientCtx, String source) {
        var client = clientCtx.getClient().getEntities();
        var command = new CollectCoordinatorRequestCollectCommandDTO();
        command.setSource(source);
        client.signalEntity(COLLECT_COORDINATOR_ENTITY_ID, DISPATCH.methodName(), command);
        return COLLECT_COORDINATOR_ENTITY_ID.toString();
    }
}
