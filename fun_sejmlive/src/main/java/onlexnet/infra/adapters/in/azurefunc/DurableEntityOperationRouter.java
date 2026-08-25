package onlexnet.infra.adapters.in.azurefunc;

import java.util.List;
import java.util.Objects;

/**
 * Resolves incoming durable entity operation names to typed contract bindings.
 */
public final class DurableEntityOperationRouter {

    private DurableEntityOperationRouter() {
    }

    public static <T extends DurableEntityContract> DurableEntityOperationBinding<T, ?> resolve(
            Class<?> targetType,
            String requestedMethod,
            List<DurableEntityOperationBinding<T, ?>> bindings) {
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(bindings, "bindings must not be null");

        return bindings.stream()
                .filter(binding -> binding.matches(requestedMethod))
                .findFirst()
                .orElseThrow(() -> unsupportedOperation(targetType, requestedMethod));
    }

    private static UnsupportedOperationException unsupportedOperation(Class<?> targetType, String requestedMethod) {
        return new UnsupportedOperationException(
                "Entity '" + targetType.getSimpleName() + "' does not support operation '" + requestedMethod + "'.");
    }
}
