package onlexnet.infra.adapters.in.azurefunc.collectcoordinator;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;

import com.microsoft.durabletask.NewOrchestrationInstanceOptions;
import com.microsoft.durabletask.TaskEntity;
import com.microsoft.durabletask.TaskEntityContext;
import com.microsoft.durabletask.TaskEntityOperation;

import lombok.RequiredArgsConstructor;
import onlexnet.app.usecases.CollectCoordinatorDecider;
import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.base.TaskEntityLifecycleContext;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectCompletedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectFailedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorDispatchCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorForceStartNextCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorRequestCollectCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInputDTO;

@Component
@RequiredArgsConstructor
public class CollectCoordinatorEntity implements TaskEntity, CollectCoordinatorContractV1 {

    private static final CollectCoordinatorDecider DECIDER = new CollectCoordinatorDecider();
    // private static final String DELETE_OPERATION_NAME = "delete";
    private final JsonValidator jsonValidator;
    private EntityState state = None.INSTANCE;
    private TaskEntityLifecycleContext context = TaskEntityLifecycleContext.uninitialized();

    protected Class<Some> getStateType() {
        return Some.class;
    }

    protected Some initializeState(TaskEntityOperation operation) {
        return new Some();
    }

    @Override
    public @Nullable Object run(TaskEntityOperation operation) {
        this.context = TaskEntityLifecycleContext.initialized(operation.getContext());

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
    public void dispatch(CollectCoordinatorDispatchCommandDTO command) {
        if (command == null) {
            throw new IllegalArgumentException("Collect coordinator command payload is required");
        }

        switch (command) {
            case CollectCoordinatorRequestCollectCommandDTO requestCollectCommand ->
                    handleRequestCollect(requestCollectCommand.getSource());
            case CollectCoordinatorCollectCompletedCommandDTO collectCompletedCommand ->
                    handleCollectCompleted(collectCompletedCommand);
            case CollectCoordinatorCollectFailedCommandDTO collectFailedCommand ->
                    handleCollectFailed(collectFailedCommand);
            case CollectCoordinatorForceStartNextCommandDTO forceStartNextCommand ->
                    handleForceStartNext(forceStartNextCommand.getSource());
            default -> throw new UnsupportedOperationException(
                    "Unsupported collect coordinator dispatch command type: " + command.getClass().getName());
        }
    }

    private void handleRequestCollect(String source) {
        var decision = DECIDER.decide(
                requireState().toDeciderState(),
                new CollectCoordinatorDecider.RequestCollect(source));
        applyDecision(decision);
    }

    private void handleCollectCompleted(CollectCoordinatorCollectCompletedCommandDTO command) {
        var validatedCompletion = this.jsonValidator.validateReceived(
                JsonValidator.COLLECT_COMPLETION,
                command.getCompletion());
        var decision = DECIDER.decide(
                requireState().toDeciderState(),
                new CollectCoordinatorDecider.CollectCompleted(validatedCompletion.getOrchestrationInstanceId()));
        applyDecision(decision);
    }

    private void handleCollectFailed(CollectCoordinatorCollectFailedCommandDTO command) {
        var validatedFailure = this.jsonValidator.validateReceived(
                JsonValidator.COLLECT_FAILURE,
                command.getFailure());
        var decision = DECIDER.decide(
                requireState().toDeciderState(),
                new CollectCoordinatorDecider.CollectFailed(
                        validatedFailure.getOrchestrationInstanceId(),
                        validatedFailure.getMessage()));
        applyDecision(decision);
    }

    private void handleForceStartNext(String source) {
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
            return;
        }
        if (decision.effect() instanceof CollectCoordinatorDecider.Effect.StartCollectRunDelayed startCollectRunDelayed) {
            startNextRun(startCollectRunDelayed.source(), Instant.now().plus(startCollectRunDelayed.delay()));
        }
    }

    private void startNextRun(String source) {
        startNextRun(source, null);
    }

    private void startNextRun(String source, @Nullable Instant startTime) {
        var options = new NewOrchestrationInstanceOptions();
        if (startTime != null) {
            options.setStartTime(startTime);
        }
        var orchestrationInput = new CollectOrchestrationInputDTO();
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

    private Some requireState() {
        if (this.state instanceof Some initializedState) {
            return initializedState;
        }
        throw new IllegalStateException("state must be initialized in run() before contract dispatch");
    }

    private TaskEntityContext requireContext() {
        return this.context.requireInitialized("context must be initialized in run() before contract dispatch");
    }
}

sealed interface EntityState permits Some, None {
}

enum None implements EntityState { INSTANCE }