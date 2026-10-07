package io.portfolio.ledgerqa.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.portfolio.ledgerqa.db.model.BalanceRecord;
import io.portfolio.ledgerqa.domain.TransactionSource;

import io.restassured.response.Response;
import io.portfolio.ledgerqa.model.responses.CreateBalanceResponse;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.model.responses.CreateMultiSourceTransactionResponse;
import io.portfolio.ledgerqa.model.responses.CreateTransactionResponse;

import io.portfolio.ledgerqa.assertions.LedgerInvariantAssertions;

import io.portfolio.ledgerqa.testsupport.TestData;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Epic("Ledger Quality Platform")
@Feature("Balances")
@Tag("functional")
class MultisourceAndDestinationTests extends FunctionalTestBase {

        @Test
        @Tag("FUN-10A")
        @Story("FUN-10 - Multi-source fixed distribution")
        @DisplayName("should debit each source by its fixed distribution and credit the destination with the total")
        void shouldSplitTransactionAcrossSourcesUsingFixedDistribution() {

                long fundingAmount = 10_000;
                long totalTransferAmount = 10_000;
                long source1Distribution = 3_000;
                long source2Distribution = 7_000;

                String currency = "NGN";
                int precision = 100;

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("fun-10-ledger"));

                CreateBalanceResponse worldBalance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source1Balance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source2Balance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse destinationBalance = createBalance(ledger.ledgerId(), currency);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source1Balance.balanceId(),
                                fundingAmount,
                                currency,
                                0,
                                precision);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source2Balance.balanceId(),
                                fundingAmount,
                                currency,
                                0,
                                precision);

                // Database snapshots before transfer
                BalanceRecord source1BeforeTransfer = fetchPersistedBalance(source1Balance.balanceId());

                BalanceRecord source2BeforeTransfer = fetchPersistedBalance(source2Balance.balanceId());

                BalanceRecord destinationBeforeTransfer = fetchPersistedBalance(destinationBalance.balanceId());

                Allure.step("Verify multi-source transfer preconditions", () -> {

                        assertThat(source1BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(source2BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(destinationBeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                List<TransactionSource> sources = List.of(
                                TransactionSource.fixed(
                                                source1Balance.balanceId(),
                                                source1Distribution),

                                TransactionSource.fixed(
                                                source2Balance.balanceId(),
                                                source2Distribution));

                // When
                CreateMultiSourceTransactionResponse response = makeMultiSourceTransfer(
                                sources,
                                destinationBalance.balanceId(),
                                totalTransferAmount,
                                currency,
                                precision,
                                true, true);

                // Database snapshots after transfer
                BalanceRecord source1AfterTransfer = fetchPersistedBalance(source1Balance.balanceId());

                BalanceRecord source2AfterTransfer = fetchPersistedBalance(source2Balance.balanceId());

                BalanceRecord destinationAfterTransfer = fetchPersistedBalance(destinationBalance.balanceId());

                // Then
                Allure.step("Verify multi-source transaction was applied", () -> {
                        assertThat(response.status())
                                        .isEqualTo("APPLIED");

                        assertThat(response.preciseAmount())
                                        .isEqualTo(totalTransferAmount);
                });

                Allure.step("Verify each source was debited by its exact fixed distribution", () -> {

                        assertThat(source1AfterTransfer.debitBalance())
                                        .isEqualByComparingTo(
                                                        source1BeforeTransfer.debitBalance()
                                                                        .add(BigDecimal.valueOf(source1Distribution)));

                        assertThat(source1AfterTransfer.balance())
                                        .isEqualByComparingTo(
                                                        source1BeforeTransfer.balance()
                                                                        .subtract(BigDecimal
                                                                                        .valueOf(source1Distribution)));

                        assertThat(source2AfterTransfer.debitBalance())
                                        .isEqualByComparingTo(
                                                        source2BeforeTransfer.debitBalance()
                                                                        .add(BigDecimal.valueOf(source2Distribution)));

                        assertThat(source2AfterTransfer.balance())
                                        .isEqualByComparingTo(
                                                        source2BeforeTransfer.balance()
                                                                        .subtract(BigDecimal
                                                                                        .valueOf(source2Distribution)));
                });

                Allure.step("Verify destination received the total source distribution", () -> {

                        assertThat(destinationAfterTransfer.creditBalance())
                                        .isEqualByComparingTo(
                                                        destinationBeforeTransfer.creditBalance()
                                                                        .add(BigDecimal.valueOf(totalTransferAmount)));

                        assertThat(destinationAfterTransfer.balance())
                                        .isEqualByComparingTo(
                                                        destinationBeforeTransfer.balance()
                                                                        .add(BigDecimal.valueOf(totalTransferAmount)));
                });

                Allure.step("Verify total source debits equal destination credit", () -> {

                        BigDecimal source1DebitDelta = source1AfterTransfer.debitBalance()
                                        .subtract(source1BeforeTransfer.debitBalance());

                        BigDecimal source2DebitDelta = source2AfterTransfer.debitBalance()
                                        .subtract(source2BeforeTransfer.debitBalance());

                        BigDecimal totalSourceDebits = source1DebitDelta.add(source2DebitDelta);

                        BigDecimal destinationCreditDelta = destinationAfterTransfer.creditBalance()
                                        .subtract(destinationBeforeTransfer.creditBalance());

                        assertThat(totalSourceDebits)
                                        .isEqualByComparingTo(destinationCreditDelta);

                        assertThat(totalSourceDebits)
                                        .isEqualByComparingTo(BigDecimal.valueOf(totalTransferAmount));
                });

                Allure.step("Verify INV-001 holds for all affected balances", () -> {
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(source1AfterTransfer);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(source2AfterTransfer);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(destinationAfterTransfer);
                });
        }

        @Test
        @Tag("FUN-10B")
        @Story("FUN-10 - Multi-source fixed distribution -rollback")
        @DisplayName("should rollback each source and destination when transfer from a source fails for any reason")
        void shouldRollBackSplitTransactionAcrossSourcesOnFailureUsingFixedDistribution() {
                long source1FundingAmount = 10_000;
                long source2FundingAmount = 6_000;

                long totalTransferAmount = 10_000;
                long source1Distribution = 2_500;
                long source2Distribution = 7_000;

                String currency = "NGN";
                int precision = 100;

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("fun-10-ledger"));

                CreateBalanceResponse worldBalance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source1Balance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source2Balance = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse destinationBalance = createBalance(ledger.ledgerId(), currency);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source1Balance.balanceId(),
                                source1FundingAmount,
                                currency,
                                0,
                                precision);

                fundBalanceFromWorld(
                                worldBalance.balanceId(),
                                source2Balance.balanceId(),
                                source2FundingAmount,
                                currency,
                                0,
                                precision);

                // Database snapshots before transfer
                BalanceRecord source1BeforeTransfer = fetchPersistedBalance(source1Balance.balanceId());

                BalanceRecord source2BeforeTransfer = fetchPersistedBalance(source2Balance.balanceId());

                BalanceRecord destinationBeforeTransfer = fetchPersistedBalance(destinationBalance.balanceId());

                Allure.step("Verify multi-source transfer preconditions", () -> {

                        assertThat(source1BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(source1FundingAmount));

                        assertThat(source2BeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(source2FundingAmount));

                        assertThat(destinationBeforeTransfer.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                List<TransactionSource> sources = buildSources(
                                List.of( TransactionSource
                                                .fixed(source1Balance.balanceId(),
                                                      source1Distribution),

                                        TransactionSource
                                                .fixed(source2Balance.balanceId(),
                                                        source2Distribution)),
                                false);

                // When
                Response response = makeMultiSourceTransferToFail(
                                sources,
                                destinationBalance.balanceId(),
                                totalTransferAmount,
                                currency,
                                precision,
                                true, // atomic
                                true); // expect failure

                attachJson("Atomic Multi-Source Failure Response", response);

                // Characterize the actual failure response
                System.out.printf(
                                "%n===== FUN-10B ATOMIC SPLIT FAILURE RESPONSE =====%n" +
                                                "HTTP Status: %d%n" +
                                                "%s%n" +
                                                "=================================================%n",
                                response.statusCode(),
                                response.getBody().asPrettyString());

                // Database snapshots after failed transfer
                BalanceRecord source1AfterTransfer = fetchPersistedBalance(source1Balance.balanceId());

                BalanceRecord source2AfterTransfer = fetchPersistedBalance(source2Balance.balanceId());

                BalanceRecord destinationAfterTransfer = fetchPersistedBalance(destinationBalance.balanceId());

                System.out.printf("""

                                ===== FUN-10B DATABASE STATE =====

                                SOURCE 1
                                Before:
                                  credit_balance = %s
                                  debit_balance  = %s
                                  balance        = %s

                                After:
                                  credit_balance = %s
                                  debit_balance  = %s
                                  balance        = %s

                                Delta:
                                  credit_balance = %s
                                  debit_balance  = %s
                                  balance        = %s

                                SOURCE 2
                                Before:
                                  credit_balance = %s
                                  debit_balance  = %s
                                  balance        = %s

                                After:
                                  credit_balance = %s
                                  debit_balance  = %s
                                  balance        = %s

                                Delta:
                                  credit_balance = %s
                                  debit_balance  = %s
                                  balance        = %s

                                DESTINATION
                                Before:
                                  credit_balance = %s
                                  debit_balance  = %s
                                  balance        = %s

                                After:
                                  credit_balance = %s
                                  debit_balance  = %s
                                  balance        = %s

                                Delta:
                                  credit_balance = %s
                                  debit_balance  = %s
                                  balance        = %s

                                ==================================
                                """,

                                // Source 1
                                source1BeforeTransfer.creditBalance(),
                                source1BeforeTransfer.debitBalance(),
                                source1BeforeTransfer.balance(),

                                source1AfterTransfer.creditBalance(),
                                source1AfterTransfer.debitBalance(),
                                source1AfterTransfer.balance(),

                                source1AfterTransfer.creditBalance()
                                                .subtract(source1BeforeTransfer.creditBalance()),
                                source1AfterTransfer.debitBalance()
                                                .subtract(source1BeforeTransfer.debitBalance()),
                                source1AfterTransfer.balance()
                                                .subtract(source1BeforeTransfer.balance()),

                                // Source 2
                                source2BeforeTransfer.creditBalance(),
                                source2BeforeTransfer.debitBalance(),
                                source2BeforeTransfer.balance(),

                                source2AfterTransfer.creditBalance(),
                                source2AfterTransfer.debitBalance(),
                                source2AfterTransfer.balance(),

                                source2AfterTransfer.creditBalance()
                                                .subtract(source2BeforeTransfer.creditBalance()),
                                source2AfterTransfer.debitBalance()
                                                .subtract(source2BeforeTransfer.debitBalance()),
                                source2AfterTransfer.balance()
                                                .subtract(source2BeforeTransfer.balance()),

                                // Destination
                                destinationBeforeTransfer.creditBalance(),
                                destinationBeforeTransfer.debitBalance(),
                                destinationBeforeTransfer.balance(),

                                destinationAfterTransfer.creditBalance(),
                                destinationAfterTransfer.debitBalance(),
                                destinationAfterTransfer.balance(),

                                destinationAfterTransfer.creditBalance()
                                                .subtract(destinationBeforeTransfer.creditBalance()),
                                destinationAfterTransfer.debitBalance()
                                                .subtract(destinationBeforeTransfer.debitBalance()),
                                destinationAfterTransfer.balance()
                                                .subtract(destinationBeforeTransfer.balance()));

                // Then
                Allure.step("Verify atomic multi-source transaction was rejected", () -> {

                        assertThat(response.statusCode())
                                        .isBetween(400, 499);
                });

                Allure.step("Verify Source 1 was rolled back to its original state", () -> {

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        source1BeforeTransfer,
                                        source1AfterTransfer);
                });

                Allure.step("Verify failing Source 2 remained unchanged", () -> {

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        source2BeforeTransfer,
                                        source2AfterTransfer);
                });

                Allure.step("Verify destination received no funds", () -> {

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationBeforeTransfer,
                                        destinationAfterTransfer);
                });

                Allure.step("Verify INV-001 holds for all balances after rollback", () -> {

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        source1AfterTransfer);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        source2AfterTransfer);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        destinationAfterTransfer);
                });
        }

        private List<TransactionSource> buildSources(
                        List<TransactionSource> sources,
                        boolean reverseOrder) {

                List<TransactionSource> orderedSources = new ArrayList<>(sources);

                if (reverseOrder) {
                        Collections.reverse(orderedSources);
                }

                return orderedSources;
        }

}

// 1. Create one ledger.

// 2. Create a world/funding balance.

// 3. Create Source A, Source B and Destination in the same currency.

// 4. Fund both sources sufficiently.

// 5. Query PostgreSQL and save:
// sourceABefore
// sourceBBefore
// destinationBefore

// 6. Create one transaction using sources[]:

// Source A → fixed 3,000
// Source B → fixed 7,000

// Destination → 10,000

// 7. Submit the transaction.

// 8. Verify transaction is APPLIED.

// 9. Query all three balances again.

// 10. Verify Source A:
// debit_balance += 3,000
// balance -= 3,000

// 11. Verify Source B:
// debit_balance += 7,000
// balance -= 7,000

// 12. Verify Destination:
// credit_balance += 10,000
// balance += 10,000

// 13. Verify:
// Source A debit delta
// + Source B debit delta
// = Destination credit delta

// 3,000 + 7,000 = 10,000

// 14. Verify INV-001 on every affected balance.
