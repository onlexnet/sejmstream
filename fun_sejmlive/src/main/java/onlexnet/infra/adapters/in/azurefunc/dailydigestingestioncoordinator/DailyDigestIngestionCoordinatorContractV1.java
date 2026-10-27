package onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator;

import onlexnet.infra.adapters.in.azurefunc.DurableEntityContract;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorDispatchCommandDTO;

/**
 * Business operation contract accepted by the daily-digest ingestion coordinator.
 */
public interface DailyDigestIngestionCoordinatorContractV1 extends DurableEntityContract {

    /**
     * Dispatches a daily-digest ingestion coordinator command.
     */
    void dispatch(CollectCoordinatorDispatchCommandDTO command);
}
