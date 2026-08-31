package io.portfolio.ledgerqa.db;

import io.portfolio.ledgerqa.config.TestConfig;

public final class DatabaseClientFactory {

    private DatabaseClientFactory() {
    }

    public static DatabaseClient create() {

        return new DatabaseClient(
                TestConfig.databaseUrl(),
                TestConfig.databaseUser(),
                TestConfig.databasePassword());
    }
}