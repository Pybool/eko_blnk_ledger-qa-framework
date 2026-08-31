package io.portfolio.ledgerqa.testsupport;

import io.portfolio.ledgerqa.api.ApiClientFactory;
import io.portfolio.ledgerqa.db.DatabaseClient;
import io.portfolio.ledgerqa.db.DatabaseClientFactory;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

public final class EnvironmentHealthExtension implements BeforeAllCallback {

    private static final int DATABASE_TIMEOUT_SECONDS = 2;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        verifyApi();
        verifyDatabase();
    }

    private void verifyApi() {

        LedgerClient ledgerClient = ApiClientFactory.ledgerClient();

        var response = ledgerClient.health();

        assertThat(response.statusCode())
                .as("Ledger API must be reachable before tests run")
                .isBetween(200, 299);
    }

    private void verifyDatabase() throws Exception {

        DatabaseClient database = DatabaseClientFactory.create();

        try (Connection connection = database.connect()) {

            assertThat(datanase.isHealthy(DATABASE_TIMEOUT_SECONDS))
                    .as("PostgreSQL must be responsive before tests run")
                    .isTrue();
        }
    }
}