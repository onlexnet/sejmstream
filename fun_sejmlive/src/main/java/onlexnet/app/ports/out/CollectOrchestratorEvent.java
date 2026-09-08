package onlexnet.app.ports.out;

import java.util.Map;

/**
 * Collect orchestration completion event payload published to Storage Queue.
 */
public record CollectOrchestratorEvent(
        String orchestrationInstanceId,
        String source,
        int termNum,
        int collectionDate,
        Map<String, Integer> countsByType) {
}
