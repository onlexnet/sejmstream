package onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator;

import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.microsoft.durabletask.NewOrchestrationInstanceOptions;
import com.microsoft.durabletask.TaskEntityContext;
import com.microsoft.durabletask.TaskEntityOperation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onlexnet.app.usecases.DailyDigestIngestionCoordinatorDecider;
import onlexnet.app.usecases.DailyDigestIngestionState;
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
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorRuntimeObservedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInputDTO;

/**
 * Owns one loading orchestration, including scheduled retries, and reconciles correlated runtime observations.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DailyDigestIngestionCoordinatorActor implements DailyDigestIngestionCoordinatorContractV1, EntityComponent {

    private static final DailyDigestIngestionCoordinatorDecider DECIDER = new DailyDigestIngestionCoordinatorDecider();
    private final JsonValidator jsonValidator;
    private Lifecycle lifecycle = Uninitialized.INSTANCE;
    private TaskEntityLifecycleContext context = TaskEntityLifecycleContext.uninitialized();

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
        var saved = operation.getState().getState(DailyDigestIngestionCoordinatorState.class);
        lifecycle = new Initialized(saved == null ? new DailyDigestIngestionState.Idle() : saved.lifecycle());
        resolveContractOperation(operation.getName()).invoke(this, operation);
        operation.getState().setState(DailyDigestIngestionCoordinatorState.of(requireState()));
        return null;
    }

    public static DurableEntityOperationBinding<DailyDigestIngestionCoordinatorContractV1, ?> resolveContractOperation(
            String requestedMethod) {
        return DailyDigestIngestionCoordinatorContractOperations.resolveOperation(
                DailyDigestIngestionCoordinatorActor.class, requestedMethod);
    }

    @Override
    public void dispatch(CollectCoordinatorDispatchCommandDTO command) {
        if (command == null) {
            throw new IllegalArgumentException("Collect coordinator command payload is required");
        }
        var decision = DECIDER.decide(requireState(), toCommand(command));
        var next = decision.state();
        if (decision.effect() instanceof DailyDigestIngestionCoordinatorDecider.Effect.StartIngestionRun start) {
            next = startRun(start);
        }
        if (!requireState().equals(next)) {
            switch (next) {
                case DailyDigestIngestionState.Exhausted exhausted ->
                        log.error("Daily-digest recovery exhausted for instance {}; administrator recovery required",
                                exhausted.instanceId());
                case DailyDigestIngestionState.RecoveryRequired recovery ->
                        log.warn("Daily-digest recovery required: reason={}, instance={}", recovery.reason(), recovery.instanceId());
                default -> { }
            }
        }
        if (next instanceof DailyDigestIngestionState.RecoveryRequired recovery
                && recovery.reason() == DailyDigestIngestionState.Reason.LEGACY_OWNERSHIP) {
            log.error("Daily-digest starts blocked: legacy ownership requires operator inventory and migration");
        }
        lifecycle = new Initialized(next);
    }

    private DailyDigestIngestionCoordinatorDecider.Command toCommand(CollectCoordinatorDispatchCommandDTO command) {
        return switch (command) {
            case CollectCoordinatorRequestCollectCommandDTO request ->
                    new DailyDigestIngestionCoordinatorDecider.RequestIngestion(requireSource(request.getSource()));
            case CollectCoordinatorCollectCompletedCommandDTO completed -> {
                var completion = jsonValidator.validateReceived(JsonValidator.COLLECT_COMPLETION, completed.getCompletion());
                yield new DailyDigestIngestionCoordinatorDecider.IngestionCompleted(completion.getOrchestrationInstanceId());
            }
            case CollectCoordinatorCollectFailedCommandDTO failed -> {
                var failure = jsonValidator.validateReceived(JsonValidator.COLLECT_FAILURE, failed.getFailure());
                yield new DailyDigestIngestionCoordinatorDecider.IngestionFailed(
                        failure.getOrchestrationInstanceId(), failure.getMessage());
            }
            case CollectCoordinatorForceStartNextCommandDTO recovery ->
                    new DailyDigestIngestionCoordinatorDecider.ForceStartNextIngestion(requireSource(recovery.getSource()));
            case CollectCoordinatorRuntimeObservedCommandDTO observed -> {
                var instanceId = observed.getOrchestrationInstanceId();
                var status = observed.getRuntimeStatus();
                if (instanceId == null || instanceId.isBlank() || status == null) {
                    throw new IllegalArgumentException("Runtime observation requires an instance ID and recognized runtime status");
                }
                yield new DailyDigestIngestionCoordinatorDecider.RuntimeObserved(instanceId,
                        DailyDigestIngestionCoordinatorDecider.RuntimeStatus.valueOf(status.getValue()));
            }
            default -> throw new UnsupportedOperationException("Unsupported coordinator command: " + command.getClass().getName());
        };
    }

    private static String requireSource(@Nullable String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Collection command source is required");
        }
        return source;
    }

    private DailyDigestIngestionState.Owned startRun(
            DailyDigestIngestionCoordinatorDecider.Effect.StartIngestionRun start) {
        var scheduledStart = Instant.now().plus(start.delay());
        var options = new NewOrchestrationInstanceOptions().setStartTime(scheduledStart);
        var input = new CollectOrchestrationInputDTO();
        input.setCoordinatorEntityId(requireContext().getId().toString());
        input.setSource(start.source());
        jsonValidator.validateToSend(JsonValidator.COLLECT_ORCHESTRATION_INPUT, input);
        var instanceId = requireContext().startNewOrchestration(
                SejmCollectFunctions.ORCHESTRATOR_FUNCTION_NAME, input, options);
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalStateException("Durable runtime did not return the scheduled orchestration ID");
        }
        log.info("Daily-digest instance {} scheduled: attempt={}, start={}", instanceId, start.attempt(), scheduledStart);
        return new DailyDigestIngestionState.Owned(instanceId, start.attempt(), start.source(), scheduledStart, false);
    }

    private DailyDigestIngestionState requireState() {
        if (lifecycle instanceof Initialized initialized) {
            return initialized.state();
        }
        throw new IllegalStateException("Coordinator state must be initialized by runOperation before dispatch");
    }

    private TaskEntityContext requireContext() {
        return context.requireInitialized("Coordinator context must be initialized by runOperation before dispatch");
    }

    private sealed interface Lifecycle { }
    private enum Uninitialized implements Lifecycle { INSTANCE }
    private record Initialized(DailyDigestIngestionState state) implements Lifecycle { }
}
