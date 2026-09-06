package onlexnet.infra.adapters.in.azurefunc.collectcoordinator;

import onlexnet.infra.adapters.in.azurefunc.DurableEntityContract;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorDispatchCommand;

/**
 * Business operation contract accepted by the collect coordinator durable entity.
 */
public interface CollectCoordinatorContractV1 extends DurableEntityContract {

    /**
     * Dispatches a collect coordinator business command.
     */
    void dispatch(CollectCoordinatorDispatchCommand command);
}
