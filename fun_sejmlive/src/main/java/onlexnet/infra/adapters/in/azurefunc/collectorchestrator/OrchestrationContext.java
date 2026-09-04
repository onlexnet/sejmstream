package onlexnet.infra.adapters.in.azurefunc.collectorchestrator;

import java.util.List;

import com.microsoft.durabletask.EntityInstanceId;
import com.microsoft.durabletask.Task;
import com.microsoft.durabletask.TaskOptions;
import com.microsoft.durabletask.TaskOrchestrationContext;

import lombok.RequiredArgsConstructor;

/**
 * Narrow adapter for the Durable Task APIs used by the collect orchestrator.
 */
@RequiredArgsConstructor
final class OrchestrationContext {

    private final TaskOrchestrationContext delegate;

    <V> V getInput(Class<V> targetType) {
        return this.delegate.getInput(targetType);
    }

    String getInstanceId() {
        return this.delegate.getInstanceId();
    }

    <V> Task<V> waitForExternalEvent(String eventName, Class<V> eventDataType) {
        return this.delegate.waitForExternalEvent(eventName, eventDataType);
    }

    <V> Task<V> callActivity(String activityName, Object input, TaskOptions options, Class<V> resultType) {
        return this.delegate.callActivity(activityName, input, options, resultType);
    }

    Task<Task<?>> anyOf(List<Task<?>> tasks) {
        return this.delegate.anyOf(tasks);
    }

    void signalEntity(EntityInstanceId entityId, String operationName, Object input) {
        this.delegate.signalEntity(entityId, operationName, input);
    }
}