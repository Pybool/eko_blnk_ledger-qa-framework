package io.portfolio.ledgerqa.config;

public final class TestConfig {

    private TestConfig() {}

    public static String baseUrl() {
        return System.getenv()
                .getOrDefault(
                    "LEDGER_BASE_URL",
                    "http://localhost:5001"
                );
    }

    public static String databaseUrl() {
        return System.getenv()
                .getOrDefault(
                    "DATABASE_URL",
                    "jdbc:postgresql://localhost:5432/blnk"
                );
    }

    public static String databaseUser() {
        return System.getenv()
                .getOrDefault(
                    "DATABASE_USER",
                    "postgres"
                );
    }

    public static String databasePassword() {
        return System.getenv()
                .getOrDefault(
                    "DATABASE_PASSWORD",
                    "postgres"
                );
    }
}