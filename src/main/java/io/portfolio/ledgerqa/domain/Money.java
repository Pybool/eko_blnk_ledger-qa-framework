package io.portfolio.ledgerqa.domain;

import java.math.BigInteger;

public record Money(
        BigInteger preciseAmount,
        int precision,
        Currency currency
) {
    public Money {
        if (preciseAmount == null) throw new IllegalArgumentException("preciseAmount is required");
        if (precision <= 0) throw new IllegalArgumentException("precision must be positive");
        if (currency == null) throw new IllegalArgumentException("currency is required");
    }
}
