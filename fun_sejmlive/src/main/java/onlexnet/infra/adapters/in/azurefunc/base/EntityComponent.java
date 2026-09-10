package onlexnet.infra.adapters.in.azurefunc.base;

import org.jspecify.annotations.Nullable;

import com.microsoft.durabletask.TaskEntityOperation;

import onlexnet.infra.adapters.in.azurefunc.DurableEntityContract;
import onlexnet.infra.adapters.in.azurefunc.DurableEntityOperationBinding;

/**
 * Entity component metadata and execution contract used by TaskEntityGateway routing.
 */
public interface EntityComponent {

    String entityName();

    DurableEntityOperationBinding<? extends DurableEntityContract, ?> resolveOperation(String requestedMethod);

    @Nullable Object runOperation(TaskEntityOperation operation);
}