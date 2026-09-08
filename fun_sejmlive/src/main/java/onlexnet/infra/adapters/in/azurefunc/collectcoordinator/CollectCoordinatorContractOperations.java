package onlexnet.infra.adapters.in.azurefunc.collectcoordinator;

import java.util.List;

import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;
import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationRouter;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorDispatchCommandDTO;

/**
 * Operation bindings and names for the collect coordinator durable entity business contract.
 */
public final class CollectCoordinatorContractOperations {

    public static final DurableEntityOperationBinding<CollectCoordinatorContractV1, CollectCoordinatorDispatchCommandDTO> DISPATCH =
            DurableEntityOperationBinding.of(
                    "dispatch",
                    CollectCoordinatorDispatchCommandDTO.class,
                    CollectCoordinatorContractV1::dispatch);

    public static final List<DurableEntityOperationBinding<CollectCoordinatorContractV1, ?>> BUSINESS_OPERATIONS =
            List.of(DISPATCH);

    private CollectCoordinatorContractOperations() {
    }

    public static DurableEntityOperationBinding<CollectCoordinatorContractV1, ?> resolveOperation(
            Class<?> targetType,
            String requestedMethod) {
        return DurableEntityOperationRouter.resolve(targetType, requestedMethod, BUSINESS_OPERATIONS);
    }
}
