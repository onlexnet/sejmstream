package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import java.util.List;

import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;
import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationRouter;

/**
 * Operation bindings and names for the term snapshot durable entity contract.
 */
public final class TermSnapshotReconcilerContractOperations {

    public static final DurableEntityOperationBinding<TermSnapshotReconcilerContractV1, TermSnapshotCollectedEvent>
            TERM_SNAPSHOT_COLLECTED = DurableEntityOperationBinding.of(
                    "termSnapshotCollected",
                    TermSnapshotCollectedEvent.class,
                    TermSnapshotReconcilerContractV1::termSnapshotCollected);

    public static final List<DurableEntityOperationBinding<TermSnapshotReconcilerContractV1, ?>> BUSINESS_OPERATIONS =
            List.of(TERM_SNAPSHOT_COLLECTED);

    private TermSnapshotReconcilerContractOperations() {
    }

    public static DurableEntityOperationBinding<TermSnapshotReconcilerContractV1, ?> resolveOperation(
            Class<?> targetType,
            String requestedMethod) {
        return DurableEntityOperationRouter.resolve(targetType, requestedMethod, BUSINESS_OPERATIONS);
    }
}