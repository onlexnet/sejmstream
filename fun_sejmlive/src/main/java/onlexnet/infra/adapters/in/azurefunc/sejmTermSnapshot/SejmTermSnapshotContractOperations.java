package onlexnet.infra.adapters.in.azurefunc.sejmTermSnapshot;

import java.util.List;

import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;
import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationRouter;

/**
 * Operation bindings and names for the term snapshot durable entity contract.
 */
public final class SejmTermSnapshotContractOperations {

    public static final DurableEntityOperationBinding<SejmTermSnapshotContractV1, TermSnapshotCollectedEvent>
            TERM_SNAPSHOT_COLLECTED = DurableEntityOperationBinding.of(
                    "termSnapshotCollected",
                    TermSnapshotCollectedEvent.class,
                    SejmTermSnapshotContractV1::termSnapshotCollected);

    public static final List<DurableEntityOperationBinding<SejmTermSnapshotContractV1, ?>> BUSINESS_OPERATIONS =
            List.of(TERM_SNAPSHOT_COLLECTED);

    private SejmTermSnapshotContractOperations() {
    }

    public static DurableEntityOperationBinding<SejmTermSnapshotContractV1, ?> resolveOperation(
            Class<?> targetType,
            String requestedMethod) {
        return DurableEntityOperationRouter.resolve(targetType, requestedMethod, BUSINESS_OPERATIONS);
    }
}