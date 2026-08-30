package io.portfolio.ledgerqa.domain;

import java.math.BigDecimal;

public record Balance(
        String id,
        BigDecimal balance,
        BigDecimal creditBalance,
        BigDecimal debitBalance,
        long version
) {}
