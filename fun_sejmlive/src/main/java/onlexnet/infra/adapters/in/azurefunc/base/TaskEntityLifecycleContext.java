package onlexnet.infra.adapters.in.azurefunc.base;

import com.microsoft.durabletask.TaskEntityContext;

/**
 * Lifecycle wrapper for durable entity context initialized during task execution.
 */
public sealed interface TaskEntityLifecycleContext {

    static TaskEntityLifecycleContext uninitialized() {
        return Uninitialized.INSTANCE;
    }

    static TaskEntityLifecycleContext initialized(TaskEntityContext value) {
        return new Initialized(value);
    }

    default TaskEntityContext requireInitialized(String message) {
        if (this instanceof Initialized initializedContext) {
            return initializedContext.value();
        }
        throw new IllegalStateException(message);
    }

    record Initialized(TaskEntityContext value) implements TaskEntityLifecycleContext {
    }

    enum Uninitialized implements TaskEntityLifecycleContext {
        INSTANCE
    }
}