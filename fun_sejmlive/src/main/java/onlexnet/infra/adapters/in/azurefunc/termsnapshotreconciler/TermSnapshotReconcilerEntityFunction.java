package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.EntityRunner;
import com.microsoft.durabletask.azurefunctions.DurableEntityTrigger;

import lombok.RequiredArgsConstructor;
import onlexnet.infra.adapters.in.azurefunc.Log;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;

/**
 * Azure Functions durable entity entrypoint for term snapshots.
 *
 * Invariant: every invocation routed through this trigger targets exactly one durable entity instance,
 * and the instance key is the Sejm term number. Because each entity instance is term-scoped, the
 * entity state intentionally tracks only the latest snapshot for that term.
 */
@Component
@RequiredArgsConstructor
public final class TermSnapshotReconcilerEntityFunction {

    private final ObjectProvider<TermSnapshotReconcilerEntity> providerOfTermSnapshotReconcilerEntity;

    @FunctionName(SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_FUNCTION_NAME)
    public String runTermSnapshotReconcilerEntity(
            @DurableEntityTrigger(name = "entityRequest", entityName = SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME)
            String entityBatchRequest,
            ExecutionContext execCtx) {

        Log.info(execCtx, "Processing term snapshot entity batch");
        return EntityRunner.loadAndRun(entityBatchRequest, () -> providerOfTermSnapshotReconcilerEntity.getObject());
    }
}

final class TermSnapshotReconcilerState implements TermSnapshotReconcilerEntityState {
    private @Nullable TermSnapshotPayload latestSnapshot;

    public TermSnapshotReconcilerState() {
    }

    /**
     * Stores the most recent snapshot for the term represented by the durable entity key.
     */
    public @Nullable TermSnapshotPayload getLatestSnapshot() {
        return this.latestSnapshot;
    }

    public void setLatestSnapshot(@Nullable TermSnapshotPayload latestSnapshot) {
        this.latestSnapshot = latestSnapshot;
    }

    public TermSnapshotReconcilerEntity.ReconciliationOutcome handleTermSnapshotCollected(int termNum, TermSnapshotCollectedEvent event) {
        return TermSnapshotReconcilerEntity.reconcile(this, termNum, event);
    }
}