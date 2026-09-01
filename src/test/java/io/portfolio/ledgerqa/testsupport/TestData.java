package io.portfolio.ledgerqa.testsupport;

import java.util.UUID;

public final class TestData {

    private static final String RUN_ID = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);

    private TestData() {
    }

    public static String runId() {
        return RUN_ID;
    }

    public static String unique(String prefix) {
        return "%s-%s-%s".formatted(
                prefix,
                RUN_ID,
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8));
    }
}