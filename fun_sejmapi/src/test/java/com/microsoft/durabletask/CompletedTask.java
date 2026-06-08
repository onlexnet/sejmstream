package com.microsoft.durabletask;

import java.util.concurrent.CompletableFuture;

/**
 * Completed task implementation for unit tests.
 *
 * @param <V> task result type
 */
public final class CompletedTask<V> extends Task<V> {

    public CompletedTask(final V result) {
        super(CompletableFuture.completedFuture(result));
    }

    @Override
    public V await() {
        return this.future.join();
    }
}