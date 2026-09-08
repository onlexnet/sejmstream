package onlexnet.infra.adapters.in.azurefunc.collectactivity;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onlexnet.app.ports.out.CollectOrchestratorEvent;
import onlexnet.app.ports.out.CollectOrchestratorEventPublisher;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.Log;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectEventPublishRequestDTO;
import onlexnet.shared.JsonDateNumbers;

@Component
@Slf4j
@RequiredArgsConstructor
public final class SejmCollectPublishCollectEventActivityFunction {

    private final CollectOrchestratorEventPublisher eventPublisher;
    private final JsonValidator jsonValidator;

    @FunctionName(SejmCollectFunctions.ACTIVITY_PUBLISH_COLLECT_EVENT)
    public String publishCollectEvent(
            @DurableActivityTrigger(name = "request") CollectEventPublishRequestDTO request,
            ExecutionContext execCtx) {
        var validatedRequest = this.jsonValidator.validateReceived(JsonValidator.COLLECT_EVENT_PUBLISH_REQUEST, request);

        try {
            var event = new CollectOrchestratorEvent(
                    requiredText(validatedRequest.getOrchestrationInstanceId(), "orchestrationInstanceId"),
                    requiredText(validatedRequest.getSource(), "source"),
                    requiredInt(validatedRequest.getTermNum(), "termNum"),
                    requiredDateNumber(validatedRequest.getCollectionDate(), "collectionDate"),
                    requiredCounts(validatedRequest.getCountsByType(), "countsByType"));
            this.eventPublisher.publish(event);
                Log.info(execCtx, "Published collect orchestration event to Storage Queue for instance="
                    + event.orchestrationInstanceId());
            return "published";
        } catch (Exception exception) {
            log.error("Activity publishCollectEvent failed", exception);
            var failure = SejmCollectActivitySupport.buildFailureMessage(exception);
            execCtx.getLogger().severe("Activity publishCollectEvent failed: " + failure);
            throw new IllegalStateException("Failed to publish collect orchestration event: " + failure, exception);
        }
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Collect event request field " + fieldName + " must not be blank");
        }
        return value;
    }

    private static int requiredInt(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("Collect event request field " + fieldName + " must not be null");
        }
        return value;
    }

    private static int requiredDateNumber(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("Collect event request field " + fieldName + " must not be null");
        }
        JsonDateNumbers.fromYyyyMmDd(value);
        return value;
    }

    private static Map<String, Integer> requiredCounts(Map<String, Integer> value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("Collect event request field " + fieldName + " must not be null");
        }
        return Map.copyOf(value);
    }
}
