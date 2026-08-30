package onlexnet.infra.adapters.in.azurefunc;

import java.util.List;

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
