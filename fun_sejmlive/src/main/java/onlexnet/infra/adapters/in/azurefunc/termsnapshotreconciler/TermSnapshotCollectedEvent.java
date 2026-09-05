package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Event payload emitted when a new term snapshot was collected.
 */
public record TermSnapshotCollectedEvent(
        LocalDate collectionDate,
        String source,
        String orchestrationInstanceId,
        Map<String, String> interpellationFingerprints,
        List<String> writtenQuestionKeys,
        List<String> printKeys,
        List<String> billKeys) {
}