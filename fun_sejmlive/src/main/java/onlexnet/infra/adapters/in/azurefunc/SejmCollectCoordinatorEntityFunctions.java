package onlexnet.infra.adapters.in.azurefunc;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.AbstractTaskEntity;
import com.microsoft.durabletask.EntityRunner;
import com.microsoft.durabletask.NewOrchestrationInstanceOptions;
import com.microsoft.durabletask.TaskEntityOperation;
import com.microsoft.durabletask.azurefunctions.DurableEntityTrigger;

import org.springframework.stereotype.Component;

import onlexnet.app.usecases.CollectCoordinatorDecider;

@Component
public final class SejmCollectCoordinatorEntityFunctions {

    @FunctionName(SejmCollectFunctions.COORDINATOR_ENTITY_FUNCTION_NAME)
    public String runCollectCoordinatorEntity(
            @DurableEntityTrigger(name = "entityRequest", entityName = SejmCollectFunctions.COORDINATOR_ENTITY_NAME) String entityBatchRequest,
            ExecutionContext execCtx) {

        Logger.info(execCtx, "Processing collect coordinator entity batch");
        return EntityRunner.loadAndRun(entityBatchRequest, CollectCoordinatorEntity::new);
    }

    public static final class CollectCoordinatorEntity extends AbstractTaskEntity<CollectCoordinatorState> {

        private static final CollectCoordinatorDecider DECIDER = new CollectCoordinatorDecider();

        @Override
        protected Class<CollectCoordinatorState> getStateType() {
            return CollectCoordinatorState.class;
        }

        @Override
        protected CollectCoordinatorState initializeState(TaskEntityOperation operation) {
            return new CollectCoordinatorState();
        }

        public void requestCollect(String source) {
            var decision = DECIDER.decide(
                    this.state.toDeciderState(),
                    new CollectCoordinatorDecider.RequestCollect(source));
            applyDecision(decision);
        }

        public void collectCompleted(final SejmCollectFunctions.CollectCompletion completion) {
            var decision = DECIDER.decide(
                    this.state.toDeciderState(),
                    new CollectCoordinatorDecider.CollectCompleted(completion.orchestrationInstanceId()));
            applyDecision(decision);
        }

        public void collectFailed(final SejmCollectFunctions.CollectFailure failure) {
            var decision = DECIDER.decide(
                    this.state.toDeciderState(),
                    new CollectCoordinatorDecider.CollectFailed(
                            failure.orchestrationInstanceId(),
                            failure.message()));
            applyDecision(decision);
        }

        private void applyDecision(final CollectCoordinatorDecider.Decision decision) {
            this.state.apply(decision.state());
            if (decision.effect() instanceof CollectCoordinatorDecider.Effect.StartCollectRun startCollectRun) {
                startNextRun(startCollectRun.source());
            }
        }

        private void startNextRun(final String source) {
            var options = new NewOrchestrationInstanceOptions();
            this.context.startNewOrchestration(
                    SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME,
                    new SejmCollectFunctions.CollectOrchestrationInput(this.context.getId().toString(), source),
                    options);
        }
    }

}

class CollectCoordinatorState {
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
