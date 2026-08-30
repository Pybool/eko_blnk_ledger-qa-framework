package io.portfolio.ledgerqa.config;

public record TestConfig(
        String baseUrl,
        String jdbcUrl,
        String dbUser,
        String dbPassword
) {}
