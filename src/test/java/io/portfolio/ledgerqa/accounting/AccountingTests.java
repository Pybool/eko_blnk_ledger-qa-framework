package io.portfolio.ledgerqa.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import io.portfolio.ledgerqa.functional.FunctionalTestBase;

import java.math.BigDecimal;
import java.util.List;

import io.portfolio.ledgerqa.assertions.LedgerInvariantAssertions;
import io.portfolio.ledgerqa.db.model.BalanceRecord;
import io.portfolio.ledgerqa.domain.TransactionStateMutation;
import io.portfolio.ledgerqa.model.responses.CreateBalanceResponse;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.model.responses.CreateTransactionResponse;
import io.portfolio.ledgerqa.testsupport.TestData;
import io.qameta.allure.Allure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import io.restassured.response.Response;

public class AccountingTests extends FunctionalTestBase {
        @Test
        @Tag("accounting")
        @Tag("ACC-01")
        @Epic("Accounting Invariants")
        @Story("ACC-01 - Derived balance integrity")
        @DisplayName("should maintain derived balance integrity across all affected balances")
        void shouldMaintainDerivedBalanceIntegrityAcrossAllAffectedBalances() {

                long fundingAmount = 8_000;
                long transferAmount = 2_000;
                String currency = "NGN";
                int precision = 100;
                long overdraftLimit = 0;

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("acc-01-ledger"));

                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source1 = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source2 = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);

                fundBalanceFromWorld(world.balanceId(),
                                source1.balanceId(), fundingAmount, currency, overdraftLimit, precision);

                fundBalanceFromWorld(world.balanceId(),
                                source2.balanceId(), fundingAmount, currency, overdraftLimit, precision);

                // Database snapshots before transfer
                BalanceRecord source1Before = fetchPersistedBalance(source1.balanceId());

                BalanceRecord source2Before = fetchPersistedBalance(source2.balanceId());

                BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify source and destination balances before transfer", () -> {

                        assertThat(source1Before.creditBalance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(source2Before.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.creditBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                makeTransfer(
                                source1.balanceId(),
                                destination.balanceId(),
                                transferAmount, currency, precision,
                                false,
                                overdraftLimit,
                                true, false);

                makeTransfer(
                                source2.balanceId(),
                                destination.balanceId(),
                                transferAmount, currency, precision,
                                false,
                                overdraftLimit,
                                true, false);

                // Then fetch databasse persisted data Via SQl queriies
                BalanceRecord worldBalance = fetchPersistedBalance(world.balanceId());
                BalanceRecord source1After = fetchPersistedBalance(source1.balanceId());
                BalanceRecord source2After = fetchPersistedBalance(source2.balanceId());
                BalanceRecord destinationAfter = fetchPersistedBalance(destination.balanceId());

                LedgerInvariantAssertions.assertMultiDerivedBalanceInvariant(
                                List.of(worldBalance, source1After, source2After, destinationAfter));

        }

        @Test
        @Tag("accounting")
        @Tag("ACC-02")
        @Epic("Accounting Invariants")
        @Story("ACC-02 - Double-entry accounting integrity")
        @DisplayName("should maintain equal total credits and debits after a completed transfer")
        void shouldMaintainDoubleEntryAccountingForCompletedTransfer() {
                long fundingAmount = 20_000;
                long transferAmount = 7_500;
                String currency = "NGN";
                int precision = 100;
                long overdraftLimit = 0;

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("acc-02-ledger"));

                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);
                fundBalanceFromWorld(world.balanceId(), source.balanceId(), fundingAmount, currency, overdraftLimit,
                                precision);

                // Database snapshots before transfer
                BalanceRecord sourceBefore = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify source and destination balances before transfer", () -> {

                        assertThat(sourceBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(sourceBefore.creditBalance())
                                        .isEqualTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(destinationBefore.creditBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                CreateTransactionResponse transferResponse = makeTransfer(source.balanceId(),
                                destination.balanceId(),
                                transferAmount, currency, precision,
                                false,
                                overdraftLimit,
                                true, false);

                Allure.step("Verify that the transfer reached 'APPLIED' status successfully", () -> {
                        assertThat(transferResponse.status()).isEqualTo("APPLIED");
                });

                BalanceRecord sourceAfter = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationAfter = fetchPersistedBalance(destination.balanceId());

                BigDecimal totalCreditDelta = sourceAfter.creditBalance().subtract(sourceBefore.creditBalance())
                                .add(destinationAfter.creditBalance().subtract(destinationBefore.creditBalance()));

                BigDecimal totalDebitDelta = sourceAfter.debitBalance().subtract(sourceBefore.debitBalance())
                                .add(destinationAfter.debitBalance().subtract(destinationBefore.debitBalance()));

                Allure.step("Verify double-entry accounting integrity", () -> {

                        assertThat(totalCreditDelta)
                                        .as("Total credits must equal total debits")
                                        .isEqualByComparingTo(totalDebitDelta);

                        assertThat(totalCreditDelta)
                                        .as("Total credits must equal transfer amount")
                                        .isEqualByComparingTo(BigDecimal.valueOf(transferAmount));

                        assertThat(totalDebitDelta)
                                        .as("Total debits must equal transfer amount")
                                        .isEqualByComparingTo(BigDecimal.valueOf(transferAmount));

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(sourceAfter);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(destinationAfter);
                });
        }

        @Test
        @Tag("accounting")
        @Tag("ACC-03")
        @Epic("Accounting Invariants")
        @Story("ACC-03 - Inflight debit bookkeeping")
        @DisplayName("should reserve source funds without modifying settled balances while INFLIGHT")
        void shouldReserveFundsWithoutChangingSettledBalancesWhileInflight() {
                long fundingAmount = 20_000;
                long transferAmount = 7_500;
                String currency = "NGN";
                int precision = 100;
                long overdraftLimit = 0;
                BigDecimal holdAmount = BigDecimal.valueOf(transferAmount);

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("acc-02-ledger"));
                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);
                fundBalanceFromWorld(world.balanceId(), source.balanceId(), fundingAmount, currency, overdraftLimit,
                                precision);

                // Database snapshots before transfer
                BalanceRecord sourceBefore = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify source and destination balances before transfer", () -> {

                        assertThat(sourceBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(sourceBefore.creditBalance())
                                        .isEqualTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(destinationBefore.creditBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                CreateTransactionResponse inflightTransferResponse = makeTransfer(source.balanceId(),
                                destination.balanceId(),
                                transferAmount, currency, precision,
                                false,
                                overdraftLimit,
                                true, true);

                Allure.step("Verify that the transfer reached 'INFLIGHT' status successfully", () -> {
                        assertThat(inflightTransferResponse.status()).isEqualTo("INFLIGHT");
                });

                // Database snapshots after transfer
                BalanceRecord sourceAfter = fetchPersistedBalance(source.balanceId());

                Allure.step("Assert the source's inflight_debit_balance increased by exactly transferAmount", () -> {
                        assertThat(sourceBefore.inflightDebitBalance().add(holdAmount))
                                        .isEqualByComparingTo(sourceAfter.inflightDebitBalance());
                });

                Allure.step("Assert the source's inflight_balance decreased by exactly transferAmount", () -> {
                        assertThat(sourceBefore.inflightBalance().subtract(holdAmount))
                                        .isEqualByComparingTo(sourceAfter.inflightBalance());
                });

                Allure.step("Assert both balances' settled balance, credit_balance, and debit_balance remain unchanged",
                                () -> {
                                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(sourceBefore,
                                                        sourceAfter);
                                });

                BalanceRecord destinationAfter = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify inflight reservation bookkeeping", () -> {

                        BigDecimal inflightDebitDelta = sourceAfter.inflightDebitBalance()
                                        .subtract(sourceBefore.inflightDebitBalance());

                        assertThat(inflightDebitDelta)
                                        .as("Source inflight debit must increase by the reserved amount")
                                        .isEqualByComparingTo(holdAmount);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceBefore, sourceAfter);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationBefore, destinationAfter);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(sourceAfter);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(destinationAfter);
                });

        }

        @Test
        @Tag("accounting")
        @Tag("ACC-04")
        @Epic("Accounting Invariants")
        @Story("ACC-04 - Inflight commit accounting")
        @DisplayName("should clear inflight holds and settle the exact amount after commit")
        void shouldMoveInflightFundsToSettledBalancesAfterCommit() {
                long fundingAmount = 20_000;
                long transferAmount = 7_500;
                String currency = "NGN";
                int precision = 100;
                long overdraftLimit = 0;
                BigDecimal holdAmount = BigDecimal.valueOf(transferAmount);

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("acc-04-ledger"));
                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);
                fundBalanceFromWorld(world.balanceId(), source.balanceId(), fundingAmount, currency, overdraftLimit,
                                precision);

                // Database snapshots before transfer
                BalanceRecord sourceBefore = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify source and destination balances before transfer", () -> {

                        assertThat(sourceBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(sourceBefore.creditBalance())
                                        .isEqualTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(destinationBefore.creditBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                CreateTransactionResponse inflightTransferResponse = makeTransfer(source.balanceId(),
                                destination.balanceId(),
                                transferAmount, currency, precision,
                                false,
                                overdraftLimit,
                                true, true);

                Allure.step("Verify that the transfer reached 'INFLIGHT' status successfully", () -> {
                        assertThat(inflightTransferResponse.status()).isEqualTo("INFLIGHT");
                });

                // Database snapshots after transfer
                BalanceRecord sourceAfter = fetchPersistedBalance(source.balanceId());

                Allure.step("Assert the source's inflight_debit_balance increased by exactly transferAmount", () -> {
                        assertThat(sourceBefore.inflightDebitBalance().add(holdAmount))
                                        .isEqualByComparingTo(sourceAfter.inflightDebitBalance());
                });

                Response response = mutateTransactionStatus(
                                inflightTransferResponse.transactionId(),
                                TransactionStateMutation.COMMIT.getValue(),
                                true);

                String committedTransactionId = response.jsonPath()
                                .getString("transaction_id");

                assertThat(response.jsonPath().getString("status"))
                                .isEqualTo("APPLIED");

                assertThat(response.jsonPath().getString("parent_transaction"))
                                .isEqualTo(inflightTransferResponse.transactionId());

                awaitTransactionStatus(committedTransactionId, "APPLIED");
                // Database snapshots before transfer
                BalanceRecord sourceAfter2 = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationInflight = fetchPersistedBalance(destination.balanceId());

                BigDecimal amount = BigDecimal.valueOf(transferAmount);

                Allure.step("Verify inflight holds are cleared", () -> {
                        assertThat(sourceAfter2.inflightDebitBalance())
                                        .isEqualByComparingTo(sourceBefore.inflightDebitBalance());

                        assertThat(destinationInflight.inflightCreditBalance())
                                        .isEqualByComparingTo(destinationBefore.inflightCreditBalance());
                });

                LedgerInvariantAssertions
                                .assertCommitSettlesInflightReservationExactlyOnce(
                                                sourceBefore,
                                                destinationBefore,
                                                sourceAfter,
                                                destinationInflight,
                                                sourceAfter2,
                                                destinationInflight,
                                                amount);

        }

        @Test
        @Tag("accounting")
        @Tag("ACC-05")
        @Epic("Accounting Invariants")
        @Story("ACC-05 - Inflight void accounting")
        @DisplayName("should release inflight holds without modifying settled balances after void")
        void shouldReleaseInflightHoldsWithoutChangingSettledBalancesAfterVoid() {
                long fundingAmount = 20_000;
                long transferAmount = 7_500;
                String currency = "NGN";
                int precision = 100;
                long overdraftLimit = 0;
                BigDecimal holdAmount = BigDecimal.valueOf(transferAmount);

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("acc-05-ledger"));
                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);
                fundBalanceFromWorld(world.balanceId(), source.balanceId(), fundingAmount, currency, overdraftLimit,
                                precision);

                // Database snapshots before transfer
                BalanceRecord sourceBefore = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify source and destination balances before transfer", () -> {

                        assertThat(sourceBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(sourceBefore.creditBalance())
                                        .isEqualTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(destinationBefore.creditBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                CreateTransactionResponse inflightTransferResponse = makeTransfer(source.balanceId(),
                                destination.balanceId(),
                                transferAmount, currency, precision,
                                false,
                                overdraftLimit,
                                true, true);

                Allure.step("Verify that the transfer reached 'INFLIGHT' status successfully", () -> {
                        assertThat(inflightTransferResponse.status()).isEqualTo("INFLIGHT");
                });

                // Database snapshots after transfer
                BalanceRecord sourceAfter = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationAfter = fetchPersistedBalance(destination.balanceId());

                Allure.step("Assert the source's inflight_debit_balance increased by exactly transferAmount", () -> {
                        assertThat(sourceBefore.inflightDebitBalance().add(holdAmount))
                                        .isEqualByComparingTo(sourceAfter.inflightDebitBalance());
                });

                Allure.step("Assert the destination's inflight_credit_balance increased by exactly transferAmount",
                                () -> {
                                        assertThat(destinationBefore.inflightCreditBalance().add(holdAmount))
                                                        .isEqualByComparingTo(destinationAfter.inflightCreditBalance());
                                });

                Response response = mutateTransactionStatus(
                                inflightTransferResponse.transactionId(),
                                TransactionStateMutation.VOID.getValue(),
                                true);

                String committedTransactionId = response.jsonPath()
                                .getString("transaction_id");

                assertThat(response.jsonPath().getString("parent_transaction"))
                                .isEqualTo(inflightTransferResponse.transactionId());

                awaitTransactionStatus(committedTransactionId, "VOID");

                // Database snapshots before transfer
                BalanceRecord sourceAfter2 = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationAfter2 = fetchPersistedBalance(destination.balanceId());

                LedgerInvariantAssertions
                                .assertVoidReleasesInflightReservationWithoutSettlement(
                                                sourceBefore,
                                                destinationBefore,
                                                sourceAfter,
                                                destinationAfter,
                                                sourceAfter2,
                                                destinationAfter2,
                                                holdAmount);

        }

        @Test
        @Tag("accounting")
        @Tag("ACC-06")
        @Epic("Accounting Invariants")
        @Story("ACC-06 - Conservation of funds across multiple transfers")
        @DisplayName("should conserve total funds across all balances after a batch of transfers")
        void shouldConserveTotalFundsAcrossMultipleTransfers() {
                int precision = 100;
                long overdraftLimit = 0;
                long fundingAmountA = 20_000;
                long fundingAmountB = 15_000;
                long fundingAmountC = 10_000;

                String currency = "NGN";

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("acc-06-ledger"));
                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse balanceA = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse balanceB = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse balanceC = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse balanceD = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse balanceE = createBalance(ledger.ledgerId(), currency);

                fundBalanceFromWorld(world.balanceId(), balanceA.balanceId(), fundingAmountA, currency, overdraftLimit,
                                precision);

                fundBalanceFromWorld(world.balanceId(), balanceB.balanceId(), fundingAmountB, currency, overdraftLimit,
                                precision);

                fundBalanceFromWorld(world.balanceId(), balanceC.balanceId(), fundingAmountC, currency, overdraftLimit,
                                precision);

                // Database snapshots before transfer
                // BalanceRecord worldBefore = fetchPersistedBalance(world.balanceId());
                BalanceRecord balanceABefore = fetchPersistedBalance(balanceA.balanceId());
                BalanceRecord balanceBBefore = fetchPersistedBalance(balanceB.balanceId());
                BalanceRecord balanceCBefore = fetchPersistedBalance(balanceC.balanceId());
                BalanceRecord balanceDBefore = fetchPersistedBalance(balanceD.balanceId());
                BalanceRecord balanceEBefore = fetchPersistedBalance(balanceE.balanceId());

                record Transfer(
                                CreateBalanceResponse source,
                                CreateBalanceResponse destination,
                                long amount) {
                }

                List<Transfer> transfers = List.of(
                                new Transfer(balanceA, balanceB, 3_000),
                                new Transfer(balanceB, balanceC, 5_000),
                                new Transfer(balanceC, balanceD, 2_500),
                                new Transfer(balanceA, balanceD, 4_000),
                                new Transfer(balanceD, balanceB, 1_500));

                for (Transfer transfer : transfers) {
                        CreateTransactionResponse transferResponse = makeTransfer(
                                        transfer.source().balanceId(),
                                        transfer.destination().balanceId(),
                                        transfer.amount(),
                                        currency,
                                        precision,
                                        false,
                                        overdraftLimit,
                                        true,
                                        false);

                        Allure.step("Verify balances and invariant rule 'INV-01'", () -> {
                                assertThat(transferResponse.status()).isEqualTo("APPLIED");
                        });

                }

                BalanceRecord balanceAAfter = fetchPersistedBalance(balanceA.balanceId());
                BalanceRecord balanceBAfter = fetchPersistedBalance(balanceB.balanceId());
                BalanceRecord balanceCAfter = fetchPersistedBalance(balanceC.balanceId());
                BalanceRecord balanceDAfter = fetchPersistedBalance(balanceD.balanceId());
                BalanceRecord balanceEAfter = fetchPersistedBalance(balanceE.balanceId());

                List<BalanceRecord> balancesBefore = List.of(balanceABefore, balanceBBefore, balanceCBefore,
                                balanceDBefore, balanceEBefore);
                List<BalanceRecord> balancesAfter = List.of(balanceAAfter, balanceBAfter, balanceCAfter, balanceDAfter,
                                balanceEAfter);

                Allure.step("ACC-06: Verify conservation of funds", () -> {
                        LedgerInvariantAssertions.assertFundsConserved(
                                        balancesBefore,
                                        balancesAfter);

                        balancesAfter.forEach(
                                        LedgerInvariantAssertions::assertDerivedBalanceInvariant);
                });
        }

        @Test
        @Tag("accounting")
        @Tag("ACC-07")
        @Epic("Accounting Invariants")
        @Story("ACC-07 - Monetary precision consistency")
        @DisplayName("should preserve exact monetary precision across a long chain of odd-valued transfers")
        void shouldPreserveMonetaryPrecisionAcrossOddValuedTransfers() {
                String currency = "NGN";
                int precision = 100;
                long fundingAmount = 100_000;
                long overdraftLimit = 0;

                CreateLedgerResponse ledger = createLedger(TestData.unique("acc-07-ledger"));

                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse balanceA = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse balanceB = createBalance(ledger.ledgerId(), currency);

                fundBalanceFromWorld(
                                world.balanceId(),
                                balanceA.balanceId(),
                                fundingAmount,
                                currency,
                                overdraftLimit,
                                precision);

                BigDecimal expectedA = BigDecimal.valueOf(fundingAmount);
                BigDecimal expectedB = BigDecimal.ZERO;

                for (int i = 0; i < 100; i++) {

                        long amount = 101L + (i * 2L); // generate amounts to transfer

                        boolean fromA = i % 3 != 2;

                        String sourceId = fromA
                                        ? balanceA.balanceId()
                                        : balanceB.balanceId();

                        String destinationId = fromA
                                        ? balanceB.balanceId()
                                        : balanceA.balanceId();

                        CreateTransactionResponse response = makeTransfer(
                                        sourceId,
                                        destinationId,
                                        amount,
                                        currency,
                                        precision,
                                        false,
                                        overdraftLimit,
                                        true,
                                        false);

                        assertThat(response.status())
                                        .as("Transfer %s should be applied", i + 1)
                                        .isEqualTo("APPLIED");

                        BigDecimal exactAmount = BigDecimal.valueOf(amount);

                        if (fromA) {
                                expectedA = expectedA.subtract(exactAmount);
                                expectedB = expectedB.add(exactAmount);
                        } else {
                                expectedB = expectedB.subtract(exactAmount);
                                expectedA = expectedA.add(exactAmount);
                        }
                }

                BalanceRecord actualA = fetchPersistedBalance(balanceA.balanceId());

                BalanceRecord actualB = fetchPersistedBalance(balanceB.balanceId());

                BigDecimal finalExpectedA = expectedA;
                BigDecimal finalExpectedB = expectedB;

                Allure.step("Verify exact balances after 100 transfers", () -> {

                        assertThat(actualA.balance())
                                        .isEqualByComparingTo(finalExpectedA);

                        assertThat(actualB.balance())
                                        .isEqualByComparingTo(finalExpectedB);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(actualA);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(actualB);
                });

                LedgerInvariantAssertions.assertMonetaryPrecisionInvariant(
                                actualA, finalExpectedA, precision);

                LedgerInvariantAssertions.assertMonetaryPrecisionInvariant(
                                actualB, finalExpectedB, precision);

        }

}
