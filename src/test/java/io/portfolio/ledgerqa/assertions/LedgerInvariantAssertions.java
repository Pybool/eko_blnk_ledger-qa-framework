package io.portfolio.ledgerqa.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collection;

import javax.print.attribute.standard.Destination;

import io.portfolio.ledgerqa.db.model.BalanceRecord;

public final class LedgerInvariantAssertions {
        private LedgerInvariantAssertions() {
        }

        // INV-01 (multiple at once)
        public static void assertMultiDerivedBalanceInvariant(Collection<BalanceRecord> balances) {

                for (BalanceRecord balance : balances) {
                        assertDerivedBalanceInvariant(balance);
                }
        }

        // INV-01
        public static void assertDerivedBalanceInvariant(BalanceRecord balance) {
                BigDecimal expected = balance.creditBalance().subtract(balance.debitBalance());

                assertThat(balance.balance()).as("INV-001 derived balance for %s",
                                balance.balanceId())
                                .isEqualByComparingTo(expected);
        }

        // INV-02

        public static void assertSettledBalanceUnchanged(BalanceRecord before, BalanceRecord after) {
                assertThat(after.balance())
                                .isEqualByComparingTo(before.balance());
                assertThat(after.creditBalance())
                                .isEqualByComparingTo(before.creditBalance());
                assertThat(after.debitBalance())
                                .isEqualByComparingTo(before.debitBalance());

        }

        // INV-005
        public static void assertVoidReleasesInflightReservationWithoutSettlement(
                        BalanceRecord sourceBefore,
                        BalanceRecord destinationBefore,
                        BalanceRecord sourceInflight,
                        BalanceRecord destinationInflight,
                        BalanceRecord sourceAfter,
                        BalanceRecord destinationAfter,
                        BigDecimal amount) {

                assertThat(amount).isPositive();

                // Verify dat funds were actually reserved
                assertThat(sourceInflight.inflightDebitBalance()
                                .subtract(sourceBefore.inflightDebitBalance()))
                                .as("INV-005: Source reservation must equal transfer amount")
                                .isEqualByComparingTo(amount);

                assertThat(destinationInflight.inflightCreditBalance()
                                .subtract(destinationBefore.inflightCreditBalance()))
                                .as("INV-005: Destination reservation must equal transfer amount")
                                .isEqualByComparingTo(amount);

                // Verify adat void released both reservations
                assertThat(sourceAfter.inflightDebitBalance())
                                .as("INV-005: Source reservation must be released")
                                .isEqualByComparingTo(sourceBefore.inflightDebitBalance());

                assertThat(destinationAfter.inflightCreditBalance())
                                .as("INV-005: Destination reservation must be released")
                                .isEqualByComparingTo(destinationBefore.inflightCreditBalance());

                // Verify dat no settled financial movement occurred
                assertSettledBalanceUnchanged(sourceBefore, sourceAfter);
                assertSettledBalanceUnchanged(destinationBefore, destinationAfter);

                // Verify dat accounting consistency
                assertDerivedBalanceInvariant(sourceAfter);
                assertDerivedBalanceInvariant(destinationAfter);
        }

        // INV-006
        public static void assertCommitSettlesInflightReservationExactlyOnce(
                        BalanceRecord sourceBefore,
                        BalanceRecord destinationBefore,
                        BalanceRecord sourceInflight,
                        BalanceRecord destinationInflight,
                        BalanceRecord sourceAfter,
                        BalanceRecord destinationAfter,
                        BigDecimal amount) {

                assertThat(amount).isPositive();

                // Verify dat initial reservations
                assertThat(sourceInflight.inflightDebitBalance()
                                .subtract(sourceBefore.inflightDebitBalance()))
                                .as("INV-006: Source reservation must equal transfer amount")
                                .isEqualByComparingTo(amount);

                // Verify adat reservations were released
                assertThat(sourceAfter.inflightDebitBalance())
                                .as("INV-006: Source inflight debit must be cleared")
                                .isEqualByComparingTo(sourceBefore.inflightDebitBalance());

                assertThat(destinationAfter.inflightCreditBalance())
                                .as("INV-006: Destination inflight credit must be cleared")
                                .isEqualByComparingTo(destinationBefore.inflightCreditBalance());

                // Verify dat exact settlement
                assertThat(sourceAfter.debitBalance()
                                .subtract(sourceBefore.debitBalance()))
                                .as("INV-006: Source settled debit must increase exactly once")
                                .isEqualByComparingTo(amount);

                assertThat(destinationAfter.creditBalance()
                                .subtract(destinationBefore.creditBalance()))
                                .as("INV-006: Destination settled credit must increase exactly once")
                                .isEqualByComparingTo(amount);

                // Verify adat net balance movements
                assertThat(sourceAfter.balance())
                                .as("INV-006: Source net balance")
                                .isEqualByComparingTo(sourceBefore.balance().subtract(amount));

                assertThat(destinationAfter.balance())
                                .as("INV-006: Destination net balance")
                                .isEqualByComparingTo(destinationBefore.balance().add(amount));

                // Verify data unaffected settled fields
                assertThat(sourceAfter.creditBalance())
                                .isEqualByComparingTo(sourceBefore.creditBalance());

                assertThat(destinationAfter.debitBalance())
                                .isEqualByComparingTo(destinationBefore.debitBalance());

                // Verify accounting consistency
                assertDerivedBalanceInvariant(sourceAfter);
                assertDerivedBalanceInvariant(destinationAfter);
        }

        // INV-007
        public static void assertMonetaryPrecisionInvariant(
                        BalanceRecord balance,
                        BigDecimal expectedPreciseBalance,
                        int precision) {

                assertThat(precision)
                                .as("Precision must be positive")
                                .isPositive();

                assertThat(balance.balance())
                                .as("INV-007: Exact persisted balance for %s", balance.balanceId())
                                .isEqualByComparingTo(expectedPreciseBalance);

                BigDecimal expectedDecimal = expectedPreciseBalance.divide(BigDecimal.valueOf(precision));

                BigDecimal actualDecimal = balance.balance().divide(BigDecimal.valueOf(precision));

                assertThat(actualDecimal)
                                .as("INV-007: Decimal representation must preserve monetary precision")
                                .isEqualByComparingTo(expectedDecimal);

                assertDerivedBalanceInvariant(balance);
        }

        public static void assertFundsConserved(
                        Collection<BalanceRecord> before,
                        Collection<BalanceRecord> after) {

                BigDecimal totalBefore = before.stream()
                                .map(BalanceRecord::balance)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalAfter = after.stream()
                                .map(BalanceRecord::balance)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                assertThat(totalAfter)
                                .as("Total funds must remain conserved across the closed ledger set")
                                .isEqualByComparingTo(totalBefore);
        }

}
