package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Activity payload describing current collected state for one Sejm term and collection date.
 */
public record TermSnapshotPayload(
        int termNum,
        LocalDate collectionDate,
        Map<String, String> interpellationFingerprints,
        List<String> writtenQuestionKeys,
        List<String> printKeys,
        List<String> billKeys) {
}