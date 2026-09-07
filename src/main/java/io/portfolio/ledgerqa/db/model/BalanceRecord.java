package io.portfolio.ledgerqa.db.model;

import java.math.BigDecimal;

public record BalanceRecord(
        String balanceId,
        String ledgerId,
        String currency,
        BigDecimal balance,
        BigDecimal creditBalance,
        BigDecimal debitBalance,
        BigDecimal inflightBalance,
        BigDecimal inflightCreditBalance,
        BigDecimal inflightDebitBalance,
        long version
) {
}