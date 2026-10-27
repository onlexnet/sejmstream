package onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator;

import java.util.List;

import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;
import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationRouter;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorDispatchCommandDTO;

/**
 * Operation bindings and names for the daily-digest ingestion coordinator contract.
 */
public final class DailyDigestIngestionCoordinatorContractOperations {

    public static final DurableEntityOperationBinding<DailyDigestIngestionCoordinatorContractV1, CollectCoordinatorDispatchCommandDTO> DISPATCH =
            DurableEntityOperationBinding.of(
                    "dispatch",
                    CollectCoordinatorDispatchCommandDTO.class,
                    DailyDigestIngestionCoordinatorContractV1::dispatch);

    public static final List<DurableEntityOperationBinding<DailyDigestIngestionCoordinatorContractV1, ?>> BUSINESS_OPERATIONS =
            List.of(DISPATCH);

    private DailyDigestIngestionCoordinatorContractOperations() {
    }

    public static DurableEntityOperationBinding<DailyDigestIngestionCoordinatorContractV1, ?> resolveOperation(
            Class<?> targetType,
            String requestedMethod) {
        return DurableEntityOperationRouter.resolve(targetType, requestedMethod, BUSINESS_OPERATIONS);
    }
}
