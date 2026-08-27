package onlexnet.infra.adapters.in.azurefunc.sejmTermSnapshot;

import onlexnet.infra.adapters.in.azurefunc.DurableEntityContract;

/**
 * Business contract accepted by the term snapshot durable entity.
 */
public interface SejmTermSnapshotContractV1 extends DurableEntityContract {

    /**
     * Handles a newly collected term snapshot.
     */
    void termSnapshotCollected(TermSnapshotCollectedEvent event);
}