package io.portfolio.ledgerqa.db.model;

import java.math.BigDecimal;

public record TransactionRecord(
        String transactionId,
        String reference,
        String source,
        String destination,
        BigDecimal amount,
        BigDecimal preciseAmount,
        String currency,
        String status
) {
}