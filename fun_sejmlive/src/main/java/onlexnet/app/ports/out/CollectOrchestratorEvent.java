package onlexnet.app.ports.out;

import java.time.LocalDate;
import java.util.Map;

/**
 * Collect orchestration completion event payload published to Event Hub.
 */
public record CollectOrchestratorEvent(
        String orchestrationInstanceId,
        String source,
        int termNum,
        LocalDate collectionDate,
        Map<String, Integer> countsByType) {
}
