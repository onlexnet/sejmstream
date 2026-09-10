package onlexnet.infra.adapters.in.azurefunc.base;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.microsoft.durabletask.TaskEntity;
import com.microsoft.durabletask.TaskEntityOperation;

import onlexnet.shared.Guards;

/**
 * Central durable entity gateway that routes runtime invocations to the proper entity component.
 */
@Component
public final class TaskEntityGateway implements TaskEntity {

    private final Map<String, DurableEntityComponent> componentsByEntityName;

    public TaskEntityGateway(List<DurableEntityComponent> entityComponents) {
        componentsByEntityName = entityComponents.stream()
                .collect(Collectors.toUnmodifiableMap(
                        DurableEntityComponent::entityName,
                        component -> component,
                        (existing, duplicate) -> {
                            throw new IllegalStateException(
                                    "Duplicate durable entity component registration for entity name '"
                                            + existing.entityName() + "'.");
                        }));
    }

    @Override
    public @Nullable Object run(TaskEntityOperation operation) {
        var entityName = operation.getContext().getId().getName();
        var component = Guards.requireState(
                componentsByEntityName.get(entityName),
                () -> "No durable entity component registered for entity name '" + entityName + "'.");
        component.resolveOperation(operation.getName());
        return component.runOperation(operation);
    }
}
