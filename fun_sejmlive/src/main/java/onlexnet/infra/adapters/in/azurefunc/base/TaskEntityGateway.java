package onlexnet.infra.adapters.in.azurefunc.base;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.microsoft.durabletask.TaskEntity;
import com.microsoft.durabletask.TaskEntityOperation;

import lombok.RequiredArgsConstructor;
import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.collectcoordinator.CollectCoordinatorEntity;
import onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotReconcilerEntity;

/**
 * Central durable entity gateway that routes runtime invocations to the proper entity component.
 */
@Component
@RequiredArgsConstructor
public final class TaskEntityGateway implements TaskEntity {

    private final ObjectProvider<CollectCoordinatorEntity> collectCoordinatorEntityProvider;
    private final ObjectProvider<TermSnapshotReconcilerEntity> termSnapshotReconcilerEntityProvider;

    @Override
    public @Nullable Object run(TaskEntityOperation operation) {
        var entityName = operation.getContext().getId().getName();
        if (SejmCollectFunctions.COORDINATOR_ENTITY_NAME.equalsIgnoreCase(entityName)) {
            return runCollectCoordinatorEntity(operation);
        }
        if (SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME.equalsIgnoreCase(entityName)) {
            return runTermSnapshotReconcilerEntity(operation);
        }
        throw new IllegalStateException("No durable entity component registered for entity name '" + entityName + "'.");
    }

    private @Nullable Object runCollectCoordinatorEntity(TaskEntityOperation operation) {
        CollectCoordinatorEntity.resolveContractOperation(operation.getName());
        return collectCoordinatorEntityProvider.getObject().runOperation(operation);
    }

    private @Nullable Object runTermSnapshotReconcilerEntity(TaskEntityOperation operation) {
        TermSnapshotReconcilerEntity.resolveContractOperation(operation.getName());
        return termSnapshotReconcilerEntityProvider.getObject().runOperation(operation);
    }
}
