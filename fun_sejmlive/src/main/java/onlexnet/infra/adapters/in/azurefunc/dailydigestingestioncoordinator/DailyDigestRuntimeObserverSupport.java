package onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator;

import org.springframework.stereotype.Component;

import com.microsoft.durabletask.DurableTaskClient;
import com.microsoft.durabletask.EntityInstanceId;

import lombok.extern.slf4j.Slf4j;
import onlexnet.app.usecases.DailyDigestIngestionState;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCoordinatorRuntimeObservedCommandDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectRuntimeStatusDTO;

/**
 * Reads runtime metadata outside entity execution and sends correlated observations; scheduling remains actor-owned.
 */
@Component
@Slf4j
public final class DailyDigestRuntimeObserverSupport {

    private static final EntityInstanceId COORDINATOR = new EntityInstanceId(
            SejmCollectFunctions.COORDINATOR_ENTITY_NAME, SejmCollectFunctions.COORDINATOR_ENTITY_KEY);

    public void observe(DurableTaskClient client) {
        var observedInstance = COORDINATOR.toString();
        try {
            var metadata = client.getEntities().getEntityMetadata(COORDINATOR, true);
            if (metadata == null) {
                log.warn("Daily-digest observer: coordinator {} has no metadata", COORDINATOR);
                return;
            }
            var state = metadata.readStateAs(DailyDigestIngestionCoordinatorState.class);
            if (state == null) {
                throw new IllegalStateException("Coordinator metadata has no persisted state");
            }
            if (!(state.lifecycle() instanceof DailyDigestIngestionState.Owned owned)) {
                switch (state.lifecycle()) {
                    case DailyDigestIngestionState.Exhausted exhausted ->
                            log.error("Daily-digest recovery exhausted for instance {}; administrator recovery required",
                                    exhausted.instanceId());
                    case DailyDigestIngestionState.RecoveryRequired recovery ->
                            log.error("Daily-digest recovery required: reason={}, instance={}",
                                    recovery.reason(), recovery.instanceId());
                    default -> { }
                }
                return;
            }
            observedInstance = owned.instanceId();
            var runtime = client.getInstanceMetadata(observedInstance, false);
            if (runtime == null || !runtime.isInstanceFound()) {
                log.error("Daily-digest observer: runtime metadata missing for owned instance {}; ownership retained",
                        observedInstance);
                return;
            }
            var status = runtime.getRuntimeStatus();
            if (status == null || !observedInstance.equals(runtime.getInstanceId())) {
                throw new IllegalStateException("Runtime metadata has no recognized status or mismatched instance ID");
            }
            var command = new CollectCoordinatorRuntimeObservedCommandDTO();
            command.setOrchestrationInstanceId(observedInstance);
            command.setRuntimeStatus(CollectRuntimeStatusDTO.fromValue(status.name()));
            client.getEntities().signalEntity(COORDINATOR, DailyDigestIngestionCoordinatorContractOperations.DISPATCH.methodName(),
                    command);
        } catch (RuntimeException exception) {
            log.error("Daily-digest runtime observation failed for {}; ownership retained", observedInstance, exception);
            throw exception;
        }
    }
}
