package io.portfolio.ledgerqa.concurrency;

public record ConcurrentResult<T>(
        T value,
        Throwable error
) {}
