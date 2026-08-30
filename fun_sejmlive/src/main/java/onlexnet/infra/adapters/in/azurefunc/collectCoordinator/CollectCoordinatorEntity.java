package onlexnet.infra.adapters.in.azurefunc.collectCoordinator;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.microsoft.durabletask.NewOrchestrationInstanceOptions;
import com.microsoft.durabletask.TaskEntity;
import com.microsoft.durabletask.TaskEntityContext;
import com.microsoft.durabletask.TaskEntityOperation;

import lombok.RequiredArgsConstructor;
import onlexnet.app.usecases.CollectCoordinatorDecider;
import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletion;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectFailure;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInput;

@Component
@RequiredArgsConstructor
public class CollectCoordinatorEntity implements TaskEntity, CollectCoordinatorContractV1 {

    private static final CollectCoordinatorDecider DECIDER = new CollectCoordinatorDecider();
    // private static final String DELETE_OPERATION_NAME = "delete";
    private final JsonValidator jsonValidator;
    private CollectCoordinatorEntityState state = UninitializedCollectCoordinatorState.INSTANCE;
    private CollectCoordinatorEntityContext context = UninitializedCollectCoordinatorEntityContext.INSTANCE;

    protected Class<CollectCoordinatorState> getStateType() {
        return CollectCoordinatorState.class;
    }

    protected CollectCoordinatorState initializeState(TaskEntityOperation operation) {
        return new CollectCoordinatorState();
    }

    @Override
    public @Nullable Object run(TaskEntityOperation operation) {
        this.context = new InitializedCollectCoordinatorEntityContext(operation.getContext());

        var stateType = getStateType();
        var persistedState = operation.getState().getState(stateType);
        this.state = persistedState == null ? initializeState(operation) : persistedState;

        // if (DELETE_OPERATION_NAME.equalsIgnoreCase(operation.getName())) {
        //     operation.getState().deleteState();
        //     this.state = null;
        //     return null;
        // }

        var dispatchOperation = resolveContractOperation(operation.getName());
        dispatchOperation.invoke(this, operation);
        operation.getState().setState(requireState());
        return null;
    }

    public static DurableEntityOperationBinding<CollectCoordinatorContractV1, ?> resolveContractOperation(String requestedMethod) {
        return CollectCoordinatorContractOperations.resolveOperation(CollectCoordinatorEntity.class, requestedMethod);
    }

    @Override
    public void requestCollect(String source) {
        var decision = DECIDER.decide(
                requireState().toDeciderState(),
                new CollectCoordinatorDecider.RequestCollect(source));
        applyDecision(decision);
    }

    @Override
    public void collectCompleted(CollectCompletion completion) {
        var validatedCompletion = this.jsonValidator.validateReceived(
                JsonValidator.COLLECT_COMPLETION,
                completion);
        var decision = DECIDER.decide(
            requireState().toDeciderState(),
                new CollectCoordinatorDecider.CollectCompleted(validatedCompletion.getOrchestrationInstanceId()));
        applyDecision(decision);
    }

    @Override
    public void collectFailed(CollectFailure failure) {
        var validatedFailure = this.jsonValidator.validateReceived(
                JsonValidator.COLLECT_FAILURE,
                failure);
        var decision = DECIDER.decide(
                requireState().toDeciderState(),
                new CollectCoordinatorDecider.CollectFailed(
                        validatedFailure.getOrchestrationInstanceId(),
                        validatedFailure.getMessage()));
        applyDecision(decision);
    }

        @Override
        public void forceStartNext(String source) {
        var normalizedSource = source == null || source.isBlank() ? "manual-recovery" : source;
        var decision = DECIDER.decide(
            requireState().toDeciderState(),
            new CollectCoordinatorDecider.ForceStartNext(normalizedSource));
        applyDecision(decision);
        }

    private void applyDecision(CollectCoordinatorDecider.Decision decision) {
        requireState().apply(decision.state());
        if (decision.effect() instanceof CollectCoordinatorDecider.Effect.StartCollectRun startCollectRun) {
            startNextRun(startCollectRun.source());
        }
    }

    private void startNextRun(String source) {
        var options = new NewOrchestrationInstanceOptions();
        var orchestrationInput = new CollectOrchestrationInput();
        orchestrationInput.setCoordinatorEntityId(requireContext().getId().toString());
        orchestrationInput.setSource(source);
        this.jsonValidator.validateToSend(
                JsonValidator.COLLECT_ORCHESTRATION_INPUT,
                orchestrationInput);
        requireContext().startNewOrchestration(
                SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME,
                orchestrationInput,
                options);
    }

    private CollectCoordinatorState requireState() {
        if (this.state instanceof CollectCoordinatorState initializedState) {
            return initializedState;
        }
        throw new IllegalStateException("state must be initialized in run() before contract dispatch");
    }

    private TaskEntityContext requireContext() {
        if (this.context instanceof InitializedCollectCoordinatorEntityContext initializedContext) {
            return initializedContext.value();
        }
        throw new IllegalStateException("context must be initialized in run() before contract dispatch");
    }
}

sealed interface CollectCoordinatorEntityState permits CollectCoordinatorState, UninitializedCollectCoordinatorState {
}

enum UninitializedCollectCoordinatorState implements CollectCoordinatorEntityState {
    INSTANCE
}

sealed interface CollectCoordinatorEntityContext permits InitializedCollectCoordinatorEntityContext, UninitializedCollectCoordinatorEntityContext {
}

record InitializedCollectCoordinatorEntityContext(TaskEntityContext value) implements CollectCoordinatorEntityContext {
}

enum UninitializedCollectCoordinatorEntityContext implements CollectCoordinatorEntityContext {
    INSTANCE
}