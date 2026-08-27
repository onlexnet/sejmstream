package onlexnet.infra.adapters.in.azurefunc.sejmTermSnapshot;

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

@Component
@RequiredArgsConstructor
public final class SejmTermSnapshotEntityFunctions {

    private final ObjectProvider<SejmTermSnapshotEntity> providerOfSejmTermSnapshotEntity;

    @FunctionName(SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_FUNCTION_NAME)
    public String runSejmTermSnapshotEntity(
            @DurableEntityTrigger(name = "entityRequest", entityName = SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME)
            String entityBatchRequest,
            ExecutionContext execCtx) {

        Log.info(execCtx, "Processing term snapshot entity batch");
        return EntityRunner.loadAndRun(entityBatchRequest, () -> providerOfSejmTermSnapshotEntity.getObject());
    }
}

final class SejmTermSnapshotState implements SejmTermSnapshotEntityState {
    private @Nullable TermSnapshotPayload latestSnapshot;

    public SejmTermSnapshotState() {
    }

    public @Nullable TermSnapshotPayload getLatestSnapshot() {
        return this.latestSnapshot;
    }

    public void setLatestSnapshot(@Nullable TermSnapshotPayload latestSnapshot) {
        this.latestSnapshot = latestSnapshot;
    }

    public SejmTermSnapshotEntity.ReconciliationOutcome handleTermSnapshotCollected(int termNum, TermSnapshotCollectedEvent event) {
        return SejmTermSnapshotEntity.reconcile(this, termNum, event);
    }
}