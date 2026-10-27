package onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;

import com.microsoft.durabletask.NewOrchestrationInstanceOptions;
import com.microsoft.durabletask.TaskEntityContext;
import com.microsoft.durabletask.TaskEntityOperation;

import lombok.RequiredArgsConstructor;
import onlexnet.app.usecases.DailyDigestIngestionCoordinatorDecider;
import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.base.EntityComponent;
import onlexnet.infra.adapters.in.azurefunc.base.TaskEntityLifecycleContext;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectCompletedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorCollectFailedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorDispatchCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorForceStartNextCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorRequestCollectCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInputDTO;

/**
 * Prevents concurrent daily-digest ingestion orchestrations by coordinating ingestion requests
 * through durable entity state.
 *
 * <p>Its single responsibility is to apply coordinator decisions and start an ingestion
 * orchestration when requested; it does not fetch or persist Sejm data itself.</p>
 */
@Component
@RequiredArgsConstructor
public class DailyDigestIngestionCoordinatorActor implements DailyDigestIngestionCoordinatorContractV1, EntityComponent {

    private static final DailyDigestIngestionCoordinatorDecider DECIDER = new DailyDigestIngestionCoordinatorDecider();
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
    public String entityName() {
        return SejmCollectFunctions.COORDINATOR_ENTITY_NAME;
    }

    @Override
    public DurableEntityOperationBinding<DailyDigestIngestionCoordinatorContractV1, ?> resolveOperation(String requestedMethod) {
        return resolveContractOperation(requestedMethod);
    }

    @Override
    public @Nullable Object runOperation(TaskEntityOperation operation) {
        context = TaskEntityLifecycleContext.initialized(operation.getContext());

        var stateType = getStateType();
        var persistedState = operation.getState().getState(stateType);
        state = persistedState == null ? initializeState(operation) : persistedState;

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

    public static DurableEntityOperationBinding<DailyDigestIngestionCoordinatorContractV1, ?> resolveContractOperation(String requestedMethod) {
        return DailyDigestIngestionCoordinatorContractOperations.resolveOperation(DailyDigestIngestionCoordinatorActor.class, requestedMethod);
    }

    @Override
    public void dispatch(CollectCoordinatorDispatchCommandDTO command) {
        if (command == null) {
            throw new IllegalArgumentException("Collect coordinator command payload is required");
        }

        switch (command) {
            case CollectCoordinatorRequestCollectCommandDTO requestCollectCommand ->
                    handleRequestIngestion(requestCollectCommand.getSource());
            case CollectCoordinatorCollectCompletedCommandDTO collectCompletedCommand ->
                    handleIngestionCompleted(collectCompletedCommand);
            case CollectCoordinatorCollectFailedCommandDTO collectFailedCommand ->
                    handleIngestionFailed(collectFailedCommand);
            case CollectCoordinatorForceStartNextCommandDTO forceStartNextCommand ->
                    handleForceStartNextIngestion(forceStartNextCommand.getSource());
            default -> throw new UnsupportedOperationException(
                    "Unsupported daily-digest ingestion coordinator command type: " + command.getClass().getName());
        }
    }

    private void handleRequestIngestion(String source) {
        var decision = DECIDER.decide(
                requireState().toDeciderState(),
                new DailyDigestIngestionCoordinatorDecider.RequestIngestion(source));
        applyDecision(decision);
    }

    private void handleIngestionCompleted(CollectCoordinatorCollectCompletedCommandDTO command) {
        var validatedCompletion = jsonValidator.validateReceived(
                JsonValidator.COLLECT_COMPLETION,
                command.getCompletion());
        var decision = DECIDER.decide(
                requireState().toDeciderState(),
                new DailyDigestIngestionCoordinatorDecider.IngestionCompleted(
                        validatedCompletion.getOrchestrationInstanceId()));
        applyDecision(decision);
    }

    private void handleIngestionFailed(CollectCoordinatorCollectFailedCommandDTO command) {
        var validatedFailure = jsonValidator.validateReceived(
                JsonValidator.COLLECT_FAILURE,
                command.getFailure());
        var decision = DECIDER.decide(
                requireState().toDeciderState(),
                new DailyDigestIngestionCoordinatorDecider.IngestionFailed(
                        validatedFailure.getOrchestrationInstanceId(),
                        validatedFailure.getMessage()));
        applyDecision(decision);
    }

    private void handleForceStartNextIngestion(String source) {
        var normalizedSource = source == null || source.isBlank() ? "manual-recovery" : source;
        var decision = DECIDER.decide(
                requireState().toDeciderState(),
                new DailyDigestIngestionCoordinatorDecider.ForceStartNextIngestion(normalizedSource));
        applyDecision(decision);
    }

    private void applyDecision(DailyDigestIngestionCoordinatorDecider.Decision decision) {
        requireState().apply(decision.state());
        if (decision.effect() instanceof DailyDigestIngestionCoordinatorDecider.Effect.StartIngestionRun startIngestionRun) {
            startNextIngestionRun(startIngestionRun.source());
            return;
        }
        if (decision.effect() instanceof DailyDigestIngestionCoordinatorDecider.Effect.StartIngestionRunDelayed startIngestionRunDelayed) {
            startNextIngestionRun(startIngestionRunDelayed.source(), Instant.now().plus(startIngestionRunDelayed.delay()));
        }
    }

    private void startNextIngestionRun(String source) {
        startNextIngestionRun(source, null);
    }

    private void startNextIngestionRun(String source, @Nullable Instant startTime) {
        var options = new NewOrchestrationInstanceOptions();
        if (startTime != null) {
            options.setStartTime(startTime);
        }
        var orchestrationInput = new CollectOrchestrationInputDTO();
        orchestrationInput.setCoordinatorEntityId(requireContext().getId().toString());
        orchestrationInput.setSource(source);
        jsonValidator.validateToSend(
                JsonValidator.COLLECT_ORCHESTRATION_INPUT,
                orchestrationInput);
        requireContext().startNewOrchestration(
                SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME,
                orchestrationInput,
                options);
    }

    private Some requireState() {
        if (state instanceof Some initializedState) {
            return initializedState;
        }
        throw new IllegalStateException("state must be initialized in run() before contract dispatch");
    }

    private TaskEntityContext requireContext() {
        return context.requireInitialized("context must be initialized in run() before contract dispatch");
    }
}

sealed interface EntityState permits Some, None {
}

enum None implements EntityState { INSTANCE }