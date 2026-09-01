package io.portfolio.ledgerqa.assertions;

import io.portfolio.ledgerqa.domain.Balance;

import static org.assertj.core.api.Assertions.assertThat;

public final class LedgerInvariantAssertions {
    private LedgerInvariantAssertions() {}

    public static void assertDerivedBalance(Balance balance) {
        assertThat(balance.balance())
                .isEqualByComparingTo(
                        balance.creditBalance().subtract(balance.debitBalance())
                );
    }
}
