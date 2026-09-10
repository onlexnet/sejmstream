package onlexnet.infra.adapters.in.azurefunc.base;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.TaskEntityContext;
import com.microsoft.durabletask.TaskEntityOperation;

import onlexnet.infra.adapters.in.azurefunc.SejmCollectFunctions;

class TaskEntityGatewayTest {

    @Test
    void givenCoordinatorEntityNameAndDispatchOperation_whenRunning_thenDelegatesToCoordinatorEntity() {
        var operation = mock(TaskEntityOperation.class);
        var context = mock(TaskEntityContext.class);
        var coordinatorEntity = mock(DurableEntityComponent.class);
        var termSnapshotEntity = mock(DurableEntityComponent.class);

        when(coordinatorEntity.entityName()).thenReturn(
                SejmCollectFunctions.COORDINATOR_ENTITY_NAME.toLowerCase(Locale.ROOT));
        when(termSnapshotEntity.entityName()).thenReturn(
                SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME.toLowerCase(Locale.ROOT));
        var gateway = new TaskEntityGateway(List.of(coordinatorEntity, termSnapshotEntity));

        when(operation.getContext()).thenReturn(context);
        when(context.getId()).thenReturn(new EntityInstanceId(
                SejmCollectFunctions.COORDINATOR_ENTITY_NAME,
                SejmCollectFunctions.COORDINATOR_ENTITY_KEY));
        when(operation.getName()).thenReturn("dispatch");
        when(coordinatorEntity.runOperation(operation)).thenReturn(null);

        gateway.run(operation);

        verify(coordinatorEntity).resolveOperation("dispatch");
        verify(coordinatorEntity).runOperation(operation);
        verify(termSnapshotEntity, never()).runOperation(operation);
    }

    @Test
    void givenTermSnapshotEntityNameAndOperation_whenRunning_thenDelegatesToTermSnapshotEntity() {
        var operation = mock(TaskEntityOperation.class);
        var context = mock(TaskEntityContext.class);
        var coordinatorEntity = mock(DurableEntityComponent.class);
        var termSnapshotEntity = mock(DurableEntityComponent.class);

        when(coordinatorEntity.entityName()).thenReturn(
                SejmCollectFunctions.COORDINATOR_ENTITY_NAME.toLowerCase(Locale.ROOT));
        when(termSnapshotEntity.entityName()).thenReturn(
                SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME.toLowerCase(Locale.ROOT));
        var gateway = new TaskEntityGateway(List.of(coordinatorEntity, termSnapshotEntity));

        when(operation.getContext()).thenReturn(context);
        when(context.getId()).thenReturn(new EntityInstanceId(
                SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME,
                "10"));
        when(operation.getName()).thenReturn("termSnapshotCollected");
        when(termSnapshotEntity.runOperation(operation)).thenReturn(null);

        gateway.run(operation);

        verify(termSnapshotEntity).resolveOperation("termSnapshotCollected");
        verify(termSnapshotEntity).runOperation(operation);
        verify(coordinatorEntity, never()).runOperation(operation);
    }

    @Test
    void givenUnknownEntityName_whenRunning_thenThrowsDescriptiveError() {
        var operation = mock(TaskEntityOperation.class);
        var context = mock(TaskEntityContext.class);
        var coordinatorEntity = mock(DurableEntityComponent.class);
        var termSnapshotEntity = mock(DurableEntityComponent.class);

        when(coordinatorEntity.entityName()).thenReturn(
                SejmCollectFunctions.COORDINATOR_ENTITY_NAME.toLowerCase(Locale.ROOT));
        when(termSnapshotEntity.entityName()).thenReturn(
                SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME.toLowerCase(Locale.ROOT));
        var gateway = new TaskEntityGateway(List.of(coordinatorEntity, termSnapshotEntity));

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
        var coordinatorEntity = mock(DurableEntityComponent.class);
        var termSnapshotEntity = mock(DurableEntityComponent.class);

        when(coordinatorEntity.entityName()).thenReturn(
                SejmCollectFunctions.COORDINATOR_ENTITY_NAME.toLowerCase(Locale.ROOT));
        when(termSnapshotEntity.entityName()).thenReturn(
                SejmCollectFunctions.TERM_SNAPSHOT_ENTITY_NAME.toLowerCase(Locale.ROOT));
        var gateway = new TaskEntityGateway(List.of(coordinatorEntity, termSnapshotEntity));

        when(operation.getContext()).thenReturn(context);
        when(context.getId()).thenReturn(new EntityInstanceId(
                SejmCollectFunctions.COORDINATOR_ENTITY_NAME,
                SejmCollectFunctions.COORDINATOR_ENTITY_KEY));
        when(operation.getName()).thenReturn("termSnapshotCollected");
        when(coordinatorEntity.resolveOperation("termSnapshotCollected"))
                .thenThrow(new UnsupportedOperationException(
                        "Entity 'CollectCoordinatorEntity' does not support operation 'termSnapshotCollected'."));

        assertThatThrownBy(() -> gateway.run(operation))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("CollectCoordinatorEntity")
                .hasMessageContaining("termSnapshotCollected");

        verify(coordinatorEntity, never()).runOperation(operation);
        verify(termSnapshotEntity, never()).runOperation(operation);
    }
}
