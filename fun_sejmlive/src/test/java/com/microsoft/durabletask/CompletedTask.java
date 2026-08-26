package com.microsoft.durabletask;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Completed task implementation for unit tests.
 *
 * @param <V> task result type
 */
public final class CompletedTask<V> extends Task<V> {

    public CompletedTask(V result) {
        super(CompletableFuture.completedFuture(result));
    }

    private CompletedTask(CompletableFuture<V> future) {
        super(future);
    }

    @Override
    public V await() {
        return this.future.join();
    }

    @Override
    public <U> Task<U> thenApply(Function<V, U> transform) {
        return new CompletedTask<>(this.future.thenApply(transform));
    }

    @Override
    public Task<Void> thenAccept(Consumer<V> action) {
        return new CompletedTask<>(this.future.thenAccept(action));
    }
}