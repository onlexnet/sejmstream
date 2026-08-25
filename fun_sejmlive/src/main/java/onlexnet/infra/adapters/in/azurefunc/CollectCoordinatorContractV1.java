package onlexnet.infra.adapters.in.azurefunc;

import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletion;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectFailure;

/**
 * Business operation contract accepted by the collect coordinator durable entity.
 */
public interface CollectCoordinatorContractV1 extends DurableEntityContract {

    /**
     * Requests a collect run for the specified source.
     */
    void requestCollect(String source);

    /**
     * Reports successful orchestration completion.
     */
    void collectCompleted(CollectCompletion completion);

    /**
     * Reports orchestration failure details.
     */
    void collectFailed(CollectFailure failure);
}
