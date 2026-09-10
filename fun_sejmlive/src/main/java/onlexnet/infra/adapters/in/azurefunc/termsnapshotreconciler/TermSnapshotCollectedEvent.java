package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Event payload emitted when a new term snapshot was collected.
 */
public record TermSnapshotCollectedEvent(
        int collectionDate,
        String source,
        String orchestrationInstanceId,
        Map<String, String> interpellationFingerprints,
        Map<String, InterpellationPresentation> interpellationPresentation,
        List<String> writtenQuestionKeys,
        List<String> printKeys,
        List<String> billKeys) {

    public record InterpellationPresentation(@Nullable String title, @Nullable String webDescriptionUrl) {
    }
}