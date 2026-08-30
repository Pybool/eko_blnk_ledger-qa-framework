package io.portfolio.ledgerqa.observability;

import java.util.UUID;

public final class CorrelationId {
    private CorrelationId() {}

    public static String next() {
        return "qa-" + UUID.randomUUID();
    }
}
