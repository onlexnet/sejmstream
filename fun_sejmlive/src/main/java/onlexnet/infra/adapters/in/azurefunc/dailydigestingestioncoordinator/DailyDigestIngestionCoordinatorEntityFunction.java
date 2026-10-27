package onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.EntityRunner;
import com.microsoft.durabletask.azurefunctions.DurableEntityTrigger;

import lombok.RequiredArgsConstructor;
import onlexnet.app.usecases.DailyDigestIngestionCoordinatorDecider;
import onlexnet.infra.adapters.in.azurefunc.Log;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.base.TaskEntityGateway;

@Component
@RequiredArgsConstructor
public final class DailyDigestIngestionCoordinatorEntityFunction {

    private final ObjectProvider<TaskEntityGateway> taskEntityGatewayProvider;

    @FunctionName(SejmCollectFunctions.COORDINATOR_ENTITY_FUNCTION_NAME)
    public String runDailyDigestIngestionCoordinatorEntity(
        @DurableEntityTrigger(name = "entityRequest", entityName = SejmCollectFunctions.COORDINATOR_ENTITY_NAME) String entityBatchRequest,
        ExecutionContext execCtx) {

        Log.info(execCtx, "Processing daily-digest ingestion coordinator entity batch");
        return EntityRunner.loadAndRun(entityBatchRequest, () -> taskEntityGatewayProvider.getObject());
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

    public DailyDigestIngestionCoordinatorDecider.State toDeciderState() {
        return new DailyDigestIngestionCoordinatorDecider.State(this.running);
    }

    public void apply(DailyDigestIngestionCoordinatorDecider.State state) {
        this.running = state.running();
    }
}
