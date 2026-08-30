package io.portfolio.ledgerqa.domain;

public record Transaction(
        String id,
        String reference,
        TransactionStatus status,
        Money money
) {}
