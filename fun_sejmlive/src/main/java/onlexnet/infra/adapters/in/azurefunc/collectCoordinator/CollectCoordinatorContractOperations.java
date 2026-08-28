package onlexnet.infra.adapters.in.azurefunc.collectCoordinator;

import java.util.List;

import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;
import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationRouter;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletion;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectFailure;

/**
 * Operation bindings and names for the collect coordinator durable entity business contract.
 */
public final class CollectCoordinatorContractOperations {

    public static final DurableEntityOperationBinding<CollectCoordinatorContractV1, String> REQUEST_COLLECT =
            DurableEntityOperationBinding.of(
                    "requestCollect",
                    String.class,
                    CollectCoordinatorContractV1::requestCollect);

    public static final DurableEntityOperationBinding<CollectCoordinatorContractV1, CollectCompletion> COLLECT_COMPLETED =
            DurableEntityOperationBinding.of(
                    "collectCompleted",
                    CollectCompletion.class,
                    CollectCoordinatorContractV1::collectCompleted);

    public static final DurableEntityOperationBinding<CollectCoordinatorContractV1, CollectFailure> COLLECT_FAILED =
            DurableEntityOperationBinding.of(
                    "collectFailed",
                    CollectFailure.class,
                    CollectCoordinatorContractV1::collectFailed);

    public static final DurableEntityOperationBinding<CollectCoordinatorContractV1, String> FORCE_START_NEXT =
            DurableEntityOperationBinding.of(
                    "forceStartNext",
                    String.class,
                    CollectCoordinatorContractV1::forceStartNext);

    public static final List<DurableEntityOperationBinding<CollectCoordinatorContractV1, ?>> BUSINESS_OPERATIONS =
            List.of(REQUEST_COLLECT, COLLECT_COMPLETED, COLLECT_FAILED, FORCE_START_NEXT);

    private CollectCoordinatorContractOperations() {
    }

    public static DurableEntityOperationBinding<CollectCoordinatorContractV1, ?> resolveOperation(
            Class<?> targetType,
            String requestedMethod) {
        return DurableEntityOperationRouter.resolve(targetType, requestedMethod, BUSINESS_OPERATIONS);
    }
}
