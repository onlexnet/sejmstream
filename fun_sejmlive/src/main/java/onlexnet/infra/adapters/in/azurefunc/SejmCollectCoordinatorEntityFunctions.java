package onlexnet.infra.adapters.in.azurefunc;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.EntityRunner;
import com.microsoft.durabletask.azurefunctions.DurableEntityTrigger;

import lombok.RequiredArgsConstructor;
import onlexnet.app.usecases.CollectCoordinatorDecider;

@Component
@RequiredArgsConstructor
public final class SejmCollectCoordinatorEntityFunctions {

    private final ObjectProvider<CollectCoordinatorEntity> providerOfCollectCoordinatorEntity;

    @FunctionName(SejmCollectFunctions.COORDINATOR_ENTITY_FUNCTION_NAME)
    public String runCollectCoordinatorEntity(
        @DurableEntityTrigger(name = "entityRequest", entityName = SejmCollectFunctions.COORDINATOR_ENTITY_NAME) String entityBatchRequest,
        ExecutionContext execCtx) {

        Log.info(execCtx, "Processing collect coordinator entity batch");
        return EntityRunner.loadAndRun(entityBatchRequest, () -> providerOfCollectCoordinatorEntity.getObject());
    }

}

final class CollectCoordinatorState implements CollectCoordinatorEntityState {
    private boolean running;
    private int pendingRequests;

    public CollectCoordinatorState() {
    }

    public boolean isRunning() {
        return this.running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public int getPendingRequests() {
        return this.pendingRequests;
    }

    public void setPendingRequests(int pendingRequests) {
        this.pendingRequests = pendingRequests;
    }

    public CollectCoordinatorDecider.State toDeciderState() {
        return new CollectCoordinatorDecider.State(this.running, this.pendingRequests);
    }

    public void apply(final CollectCoordinatorDecider.State state) {
        this.running = state.running();
        this.pendingRequests = state.pendingRequests();
    }
}
