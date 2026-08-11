package onlexnet.infra.adapters.in.azurefunc;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.AbstractTaskEntity;
import com.microsoft.durabletask.EntityRunner;
import com.microsoft.durabletask.NewOrchestrationInstanceOptions;
import com.microsoft.durabletask.TaskEntityOperation;
import com.microsoft.durabletask.azurefunctions.DurableEntityTrigger;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import onlexnet.app.usecases.CollectCoordinatorDecider;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletion;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectFailure;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInput;

@Component
@RequiredArgsConstructor
public final class SejmCollectCoordinatorEntityFunctions {

    private final JsonValidator jsonValidator;

    @FunctionName(SejmCollectFunctions.COORDINATOR_ENTITY_FUNCTION_NAME)
    public String runCollectCoordinatorEntity(
            @DurableEntityTrigger(name = "entityRequest", entityName = SejmCollectFunctions.COORDINATOR_ENTITY_NAME) String entityBatchRequest,
            ExecutionContext execCtx) {

        Logger.info(execCtx, "Processing collect coordinator entity batch");
        return EntityRunner.loadAndRun(entityBatchRequest, () -> new CollectCoordinatorEntity(this.jsonValidator));
    }

    public static final class CollectCoordinatorEntity extends AbstractTaskEntity<CollectCoordinatorState> {

        private static final CollectCoordinatorDecider DECIDER = new CollectCoordinatorDecider();
        private final JsonValidator jsonValidator;

        CollectCoordinatorEntity(final JsonValidator jsonValidator) {
            this.jsonValidator = jsonValidator;
		}

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

        public void collectCompleted(final CollectCompletion completion) {
            var validatedCompletion = this.jsonValidator.validateReceived(
                    JsonValidator.COLLECT_COMPLETION,
					completion);
            var decision = DECIDER.decide(
                    this.state.toDeciderState(),
                    new CollectCoordinatorDecider.CollectCompleted(validatedCompletion.getOrchestrationInstanceId()));
            applyDecision(decision);
        }

        public void collectFailed(final CollectFailure failure) {
            var validatedFailure = this.jsonValidator.validateReceived(
                    JsonValidator.COLLECT_FAILURE,
					failure);
            var decision = DECIDER.decide(
                    this.state.toDeciderState(),
                    new CollectCoordinatorDecider.CollectFailed(
                            validatedFailure.getOrchestrationInstanceId(),
                            validatedFailure.getMessage()));
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
            var orchestrationInput = new CollectOrchestrationInput();
            orchestrationInput.setCoordinatorEntityId(this.context.getId().toString());
            orchestrationInput.setSource(source);
                this.jsonValidator.validateToSend(
                    JsonValidator.COLLECT_ORCHESTRATION_INPUT,
                    orchestrationInput);
            this.context.startNewOrchestration(
                    SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME,
                    orchestrationInput,
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
