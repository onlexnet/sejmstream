package onlexnet.infra.adapters.in.azurefunc.collectcoordinator;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.EntityRunner;
import com.microsoft.durabletask.azurefunctions.DurableEntityTrigger;

import lombok.RequiredArgsConstructor;
import onlexnet.app.usecases.CollectCoordinatorDecider;
import onlexnet.infra.adapters.in.azurefunc.Log;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;

@Component
@RequiredArgsConstructor
public final class SejmCollectCoordinatorEntityFunction {

    private final ObjectProvider<CollectCoordinatorEntity> entityProvider;

    @FunctionName(SejmCollectFunctions.COORDINATOR_ENTITY_FUNCTION_NAME)
    public String runCollectCoordinatorEntity(
        @DurableEntityTrigger(name = "entityRequest", entityName = SejmCollectFunctions.COORDINATOR_ENTITY_NAME) String entityBatchRequest,
        ExecutionContext execCtx) {

        Log.info(execCtx, "Processing collect coordinator entity batch");
        return EntityRunner.loadAndRun(entityBatchRequest, () -> entityProvider.getObject());
    }

}

@JsonIgnoreProperties(ignoreUnknown = true)
final class Some implements EntityState {
    private boolean running;

    public Some() {
    }

    public boolean isRunning() {
        return this.running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public CollectCoordinatorDecider.State toDeciderState() {
        return new CollectCoordinatorDecider.State(this.running);
    }

    public void apply(CollectCoordinatorDecider.State state) {
        this.running = state.running();
    }
}
