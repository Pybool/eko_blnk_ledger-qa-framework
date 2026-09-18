package io.portfolio.ledgerqa.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import io.portfolio.ledgerqa.db.model.BalanceRecord;


public final class LedgerInvariantAssertions {
    private LedgerInvariantAssertions() {
    }

    //INV-01
    public static void assertDerivedBalanceInvariant(BalanceRecord balance) {
        BigDecimal expected = balance.creditBalance()
                .subtract(balance.debitBalance());

        assertThat(balance.balance())
                .as("INV-001 derived balance for %s",
                        balance.balanceId())
                .isEqualByComparingTo(expected);
    }
}
