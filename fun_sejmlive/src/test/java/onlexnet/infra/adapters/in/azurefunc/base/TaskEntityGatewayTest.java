package onlexnet.infra.adapters.in.azurefunc.base;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.TaskEntityContext;
import com.microsoft.durabletask.TaskEntityOperation;

import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;
import onlexnet.infra.adapters.in.azurefunc.collectcoordinator.CollectCoordinatorEntity;
import onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler.TermSnapshotReconcilerEntity;

class TaskEntityGatewayTest {

    @Test
    void givenCoordinatorEntityNameAndDispatchOperation_whenRunning_thenDelegatesToCoordinatorEntity() {
        var operation = mock(TaskEntityOperation.class);
        var context = mock(TaskEntityContext.class);
        var coordinatorEntity = mock(CollectCoordinatorEntity.class);
        var termSnapshotEntity = mock(TermSnapshotReconcilerEntity.class);
        var coordinatorProvider = mockProvider(coordinatorEntity);
        var termSnapshotProvider = mockProvider(termSnapshotEntity);
        var gateway = new TaskEntityGateway(coordinatorProvider, termSnapshotProvider);

        when(operation.getContext()).thenReturn(context);
        when(context.getId()).thenReturn(new EntityInstanceId(
                SejmCollectFunctions.COORDINATOR_ENTITY_NAME,
                SejmCollectFunctions.COORDINATOR_ENTITY_KEY));
        when(operation.getName()).thenReturn("dispatch");

        gateway.run(operation);

        verify(coordinatorEntity).runOperation(operation);
        verify(termSnapshotEntity, never()).runOperation(operation);
    }

    @Test
    void givenTermSnapshotEntityNameAndOperation_whenRunning_thenDelegatesToTermSnapshotEntity() {
        var operation = mock(TaskEntityOperation.class);
        var context = mock(TaskEntityContext.class);
        var coordinatorEntity = mock(CollectCoordinatorEntity.class);
        var termSnapshotEntity = mock(TermSnapshotReconcilerEntity.class);
        var coordinatorProvider = mockProvider(coordinatorEntity);
        var termSnapshotProvider = mockProvider(termSnapshotEntity);
        var gateway = new TaskEntityGateway(coordinatorProvider, termSnapshotProvider);

        when(operation.getContext()).thenReturn(context);
        when(context.getId()).thenReturn(new EntityInstanceId(
                SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME,
                "10"));
        when(operation.getName()).thenReturn("termSnapshotCollected");

        gateway.run(operation);

        verify(termSnapshotEntity).runOperation(operation);
        verify(coordinatorEntity, never()).runOperation(operation);
    }

    @Test
    void givenUnknownEntityName_whenRunning_thenThrowsDescriptiveError() {
        var operation = mock(TaskEntityOperation.class);
        var context = mock(TaskEntityContext.class);
        var coordinatorProvider = mockProvider(mock(CollectCoordinatorEntity.class));
        var termSnapshotProvider = mockProvider(mock(TermSnapshotReconcilerEntity.class));
        var gateway = new TaskEntityGateway(coordinatorProvider, termSnapshotProvider);

        when(operation.getContext()).thenReturn(context);
        when(context.getId()).thenReturn(new EntityInstanceId("UnknownEntity", "singleton"));

        assertThatThrownBy(() -> gateway.run(operation))
                .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unknownentity");
    }

    @Test
    void givenUnsupportedOperationForKnownEntity_whenRunning_thenFailsBeforeDelegation() {
        var operation = mock(TaskEntityOperation.class);
        var context = mock(TaskEntityContext.class);
        var coordinatorEntity = mock(CollectCoordinatorEntity.class);
        var termSnapshotEntity = mock(TermSnapshotReconcilerEntity.class);
        var coordinatorProvider = mockProvider(coordinatorEntity);
        var termSnapshotProvider = mockProvider(termSnapshotEntity);
        var gateway = new TaskEntityGateway(coordinatorProvider, termSnapshotProvider);

        when(operation.getContext()).thenReturn(context);
        when(context.getId()).thenReturn(new EntityInstanceId(
                SejmCollectFunctions.COORDINATOR_ENTITY_NAME,
                SejmCollectFunctions.COORDINATOR_ENTITY_KEY));
        when(operation.getName()).thenReturn("termSnapshotCollected");

        assertThatThrownBy(() -> gateway.run(operation))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("CollectCoordinatorEntity")
                .hasMessageContaining("termSnapshotCollected");

        verify(coordinatorEntity, never()).runOperation(operation);
        verify(termSnapshotEntity, never()).runOperation(operation);
    }

    private static <T> ObjectProvider<T> mockProvider(T value) {
        @SuppressWarnings("unchecked")
        var provider = (ObjectProvider<T>) mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(value);
        return provider;
    }
}
