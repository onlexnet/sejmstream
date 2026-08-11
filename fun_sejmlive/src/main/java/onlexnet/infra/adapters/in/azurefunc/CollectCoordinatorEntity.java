package onlexnet.infra.adapters.in.azurefunc;

import org.springframework.stereotype.Component;

import com.microsoft.durabletask.AbstractTaskEntity;
import com.microsoft.durabletask.NewOrchestrationInstanceOptions;
import com.microsoft.durabletask.TaskEntityOperation;

import lombok.RequiredArgsConstructor;
import onlexnet.app.usecases.CollectCoordinatorDecider;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletion;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectFailure;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInput;

@Component
@RequiredArgsConstructor
public class CollectCoordinatorEntity extends AbstractTaskEntity<CollectCoordinatorState> {

    private static final CollectCoordinatorDecider DECIDER = new CollectCoordinatorDecider();
    private final JsonValidator jsonValidator;

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