package onlexnet.infra.adapters.in.azurefunc;

import java.util.function.BiConsumer;

import com.microsoft.durabletask.TaskEntityOperation;

/**
 * Typed binding between an incoming durable operation name and a contract method invocation.
 */
public record DurableEntityOperationBinding<T extends DurableEntityContract, P>(
        String methodName,
        Class<P> payloadType,
        BiConsumer<T, P> invoker) {

    public static <T extends DurableEntityContract, P> DurableEntityOperationBinding<T, P> of(
            String methodName,
            Class<P> payloadType,
            BiConsumer<T, P> invoker) {
        return new DurableEntityOperationBinding<>(methodName, payloadType, invoker);
    }

    public boolean matches(String requestedMethod) {
        return this.methodName.equalsIgnoreCase(requestedMethod);
    }

    public void invoke(T target, TaskEntityOperation operation) {
        var payload = operation.getInput(this.payloadType);
        this.invoker.accept(target, payload);
    }
}
