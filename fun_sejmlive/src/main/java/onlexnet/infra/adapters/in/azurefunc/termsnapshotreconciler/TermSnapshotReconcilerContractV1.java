package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import onlexnet.infra.adapters.in.azurefunc.DurableEntityContract;

/**
 * Business contract accepted by the term snapshot durable entity.
 */
public interface TermSnapshotReconcilerContractV1 extends DurableEntityContract {

    /**
     * Handles a newly collected term snapshot.
     */
    void termSnapshotCollected(TermSnapshotCollectedEvent event);
}