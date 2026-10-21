package io.portfolio.ledgerqa.accounting;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.portfolio.ledgerqa.functional.FunctionalTestBase;

import io.portfolio.ledgerqa.assertions.LedgerInvariantAssertions;
import io.portfolio.ledgerqa.db.model.BalanceRecord;
import io.portfolio.ledgerqa.db.model.TransactionRecord;
import io.portfolio.ledgerqa.domain.TransactionStateMutation;
import io.portfolio.ledgerqa.model.responses.CreateBalanceResponse;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.model.responses.CreateTransactionResponse;
import io.portfolio.ledgerqa.testsupport.TestData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;

@Epic("Ledger Quality Platform")
@Feature("Transaction Lifecycle")
@Tag("lifecycle")
public class LifecycleTests extends FunctionalTestBase {

        @Test
        @Tag("LIF-03")
        @Story("LIF-03 - Partial commit of inflight transaction")
        @DisplayName("should settle only the committed amount and correctly handle the remaining reservation")
        void shouldPartiallyCommitInflightTransaction() {
                String currency = "NGN";
                int precision = 100;
                long fundingAmount = 20_000;
                long transferAmount = 10_000;
                long partialCommitAmount = 4_000;
                long overdraftLimit = 0;
                BigDecimal holdAmount = BigDecimal.valueOf(transferAmount);
                BigDecimal amount = BigDecimal.valueOf(partialCommitAmount);

                CreateLedgerResponse ledger = createLedger(TestData.unique("lif-03-ledger"));

                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);

                CreateTransactionResponse fundingTransaction = fundBalanceFromWorld(
                                world.balanceId(),
                                source.balanceId(),
                                fundingAmount,
                                currency,
                                overdraftLimit,
                                precision);

                Allure.step("Verify that the transfer reached 'APPLIED' status successfully", () -> {
                        assertThat(fundingTransaction.status()).isEqualTo("APPLIED");
                });

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

                Allure.step("Assert the source's inflight_credit_balance remains exactly the same", () -> {
                        assertThat(sourceBefore.inflightCreditBalance())
                                        .isEqualByComparingTo(sourceAfter.inflightCreditBalance());
                });

                Allure.step("Assert the source' debit_balance remains exactly the same", () -> {
                        assertThat(sourceBefore.debitBalance())
                                        .isEqualByComparingTo(sourceAfter.debitBalance());
                });

                Allure.step("Assert the source's credit_balance remains exactly the same", () -> {
                        assertThat(sourceBefore.creditBalance())
                                        .isEqualByComparingTo(sourceAfter.creditBalance());
                });

                Allure.step("Assert the source's settled balance remains exactly the same", () -> {
                        assertThat(sourceBefore.balance())
                                        .isEqualByComparingTo(sourceAfter.balance());
                });

                Response response = mutateTransactionStatus(
                                inflightTransferResponse.transactionId(),
                                TransactionStateMutation.COMMIT.getValue(),
                                true,
                                partialCommitAmount);

                String transactionId = response.jsonPath().getString("transaction_id");
                TransactionRecord childTransaction = fetchPersistedTransaction(transactionId);

                System.out.printf(
                                "%n===== PARTIAL COMMIT CHILD TRANSACTION =====%n%s%n"
                                                + "==========================================%n",
                                childTransaction);

                assertThat(childTransaction.status())
                                .isEqualTo("APPLIED");

                assertThat(childTransaction.parentTransaction())
                                .isEqualTo(inflightTransferResponse.transactionId());

                assertThat(childTransaction.preciseAmount())
                                .isEqualTo(amount);

                // Database snapshots after transfer
                BalanceRecord sourceAfterPartialCommit = fetchPersistedBalance(source.balanceId());

                BalanceRecord destinationAfterPartialCommit = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify source settled debit increased by partial commit amount", () -> {
                        assertThat(sourceAfterPartialCommit.debitBalance()
                                        .subtract(sourceBefore.debitBalance()))
                                        .isEqualByComparingTo(amount);
                });

                Allure.step("Verify destination settled credit increased by partial commit amount", () -> {
                        assertThat(destinationAfterPartialCommit.creditBalance()
                                        .subtract(destinationBefore.creditBalance()))
                                        .isEqualByComparingTo(amount);
                });

                Allure.step("Verify source settled balance decreased by partial commit amount", () -> {
                        assertThat(sourceAfterPartialCommit.balance())
                                        .isEqualByComparingTo(
                                                        sourceBefore.balance().subtract(amount));
                });

                Allure.step("Verify destination settled balance increased by partial commit amount", () -> {
                        assertThat(destinationAfterPartialCommit.balance())
                                        .isEqualByComparingTo(
                                                        destinationBefore.balance().add(amount));
                });

                Allure.step("Verify unaffected settled accounting fields", () -> {
                        assertThat(sourceAfterPartialCommit.creditBalance())
                                        .isEqualByComparingTo(sourceBefore.creditBalance());

                        assertThat(destinationAfterPartialCommit.debitBalance())
                                        .isEqualByComparingTo(destinationBefore.debitBalance());
                });

                Allure.step("Verify INV-001 after partial commit", () -> {
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        sourceAfterPartialCommit);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        destinationAfterPartialCommit);
                });

        }

        @Test
        @Tag("LIF-04")
        @Story("LIF-04 - Duplicate inflight commit prevention")
        @DisplayName("should reject a second commit without settling funds twice")
        void shouldRejectDuplicateCommitWithoutDoubleSettlement() {
                long fundingAmount = 20_000;
                long transferAmount = 9_500;
                String currency = "NGN";
                int precision = 100;
                long overdraftLimit = 0;
                BigDecimal amount = BigDecimal.valueOf(transferAmount);

                CreateLedgerResponse ledger = createLedger(TestData.unique("lif-04-ledger"));
                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);

                CreateTransactionResponse fundingTransaction = fundBalanceFromWorld(
                                world.balanceId(),
                                source.balanceId(),
                                fundingAmount,
                                currency,
                                overdraftLimit,
                                precision);

                Allure.step("Verify funding transaction was applied", () -> {
                        assertThat(fundingTransaction.status()).isEqualTo("APPLIED");
                });

                BalanceRecord sourceBefore = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify initial accounting state", () -> {
                        assertThat(sourceBefore.creditBalance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(sourceBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(sourceBefore.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(destinationBefore.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.creditBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(sourceBefore);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(destinationBefore);
                });

                CreateTransactionResponse inflightTransferResponse = makeTransfer(
                                source.balanceId(),
                                destination.balanceId(),
                                transferAmount,
                                currency,
                                precision,
                                false,
                                overdraftLimit,
                                true,
                                true);

                String inflightTransactionId = inflightTransferResponse.transactionId();

                Allure.step("Verify transaction entered INFLIGHT state", () -> {
                        assertThat(inflightTransferResponse.status())
                                        .isEqualTo("INFLIGHT");
                });

                BalanceRecord sourceInflight = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationInflight = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify inflight reservation was created", () -> {
                        assertThat(sourceInflight.inflightDebitBalance())
                                        .isEqualByComparingTo(
                                                        sourceBefore.inflightDebitBalance().add(amount));

                        assertThat(sourceInflight.inflightCreditBalance())
                                        .isEqualByComparingTo(
                                                        sourceBefore.inflightCreditBalance());
                });

                Allure.step("Verify inflight transfer did not mutate settled balances", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceBefore,
                                        sourceInflight);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationBefore,
                                        destinationInflight);
                });

                Response firstCommitResponse = mutateTransactionStatus(
                                inflightTransactionId,
                                TransactionStateMutation.COMMIT.getValue(),
                                true,
                                null);

                String committedTransactionId = firstCommitResponse.jsonPath()
                                .getString("transaction_id");

                Allure.step("Verify first commit succeeded", () -> {
                        assertThat(firstCommitResponse.statusCode())
                                        .isBetween(200, 299);

                        assertThat(committedTransactionId)
                                        .isNotBlank();

                        assertThat(firstCommitResponse.jsonPath().getString("status"))
                                        .isEqualTo("APPLIED");

                        assertThat(firstCommitResponse.jsonPath().getString("parent_transaction"))
                                        .isEqualTo(inflightTransactionId);
                });

                awaitTransactionStatus(committedTransactionId, "APPLIED");

                TransactionRecord committedTransaction = fetchPersistedTransaction(committedTransactionId);

                Allure.step("Verify committed child transaction", () -> {
                        assertThat(committedTransaction.status())
                                        .isEqualTo("APPLIED");

                        assertThat(committedTransaction.parentTransaction())
                                        .isEqualTo(inflightTransactionId);

                        assertThat(committedTransaction.preciseAmount())
                                        .isEqualByComparingTo(amount);
                });

                BalanceRecord sourceAfterFirstCommit = fetchPersistedBalance(source.balanceId());

                BalanceRecord destinationAfterFirstCommit = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify first commit settled the exact amount", () -> {
                        assertThat(sourceAfterFirstCommit.debitBalance())
                                        .isEqualByComparingTo(
                                                        sourceBefore.debitBalance().add(amount));

                        assertThat(destinationAfterFirstCommit.creditBalance())
                                        .isEqualByComparingTo(
                                                        destinationBefore.creditBalance().add(amount));

                        assertThat(sourceAfterFirstCommit.balance())
                                        .isEqualByComparingTo(
                                                        sourceBefore.balance().subtract(amount));

                        assertThat(destinationAfterFirstCommit.balance())
                                        .isEqualByComparingTo(
                                                        destinationBefore.balance().add(amount));
                });

                Allure.step("Verify inflight reservations were released", () -> {
                        assertThat(sourceAfterFirstCommit.inflightDebitBalance())
                                        .isEqualByComparingTo(
                                                        sourceBefore.inflightDebitBalance());

                        assertThat(destinationAfterFirstCommit.inflightCreditBalance())
                                        .isEqualByComparingTo(
                                                        destinationBefore.inflightCreditBalance());
                });

                LedgerInvariantAssertions.assertCommitSettlesInflightReservationExactlyOnce(
                                sourceBefore,
                                destinationBefore,
                                sourceInflight,
                                destinationInflight,
                                sourceAfterFirstCommit,
                                destinationAfterFirstCommit,
                                amount);

                Response responseFail = mutateTransactionStatus(
                                inflightTransactionId,
                                TransactionStateMutation.COMMIT.getValue(),
                                true,
                                null);

                Allure.step("Verify duplicate commit is rejected", () -> {
                        assertThat(responseFail.statusCode())
                                        .as("Duplicate commit must return HTTP 409")
                                        .isEqualTo(409);

                        assertThat(responseFail.jsonPath().getString("error_detail.code"))
                                        .as("Duplicate commit must return TXN_ALREADY_COMMITTED")
                                        .isEqualTo("TXN_ALREADY_COMMITTED");
                });

                BalanceRecord sourceAfterSecondCommit = fetchPersistedBalance(source.balanceId());

                BalanceRecord destinationAfterSecondCommit = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify duplicate commit did not mutate source settled balances", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceAfterFirstCommit,
                                        sourceAfterSecondCommit);
                });

                Allure.step("Verify duplicate commit did not mutate destination settled balances", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationAfterFirstCommit,
                                        destinationAfterSecondCommit);
                });

                Allure.step("Verify duplicate commit did not mutate source reservations", () -> {
                        assertThat(sourceAfterSecondCommit.inflightDebitBalance())
                                        .isEqualByComparingTo(
                                                        sourceAfterFirstCommit.inflightDebitBalance());

                        assertThat(sourceAfterSecondCommit.inflightCreditBalance())
                                        .isEqualByComparingTo(
                                                        sourceAfterFirstCommit.inflightCreditBalance());
                });

                Allure.step("Verify duplicate commit did not mutate destination reservations", () -> {
                        assertThat(destinationAfterSecondCommit.inflightDebitBalance())
                                        .isEqualByComparingTo(
                                                        destinationAfterFirstCommit.inflightDebitBalance());

                        assertThat(destinationAfterSecondCommit.inflightCreditBalance())
                                        .isEqualByComparingTo(
                                                        destinationAfterFirstCommit.inflightCreditBalance());
                });

                Allure.step("Verify original settlement remains applied and unchanged", () -> {
                        TransactionRecord persistedCommit = fetchPersistedTransaction(committedTransactionId);

                        assertThat(persistedCommit.status())
                                        .isEqualTo("APPLIED");

                        assertThat(persistedCommit.parentTransaction())
                                        .isEqualTo(inflightTransactionId);

                        assertThat(persistedCommit.preciseAmount())
                                        .isEqualByComparingTo(amount);
                });

                Allure.step("Verify INV-001 after rejected duplicate commit", () -> {
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        sourceAfterSecondCommit);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        destinationAfterSecondCommit);
                });

                Allure.step("Verify INV-004: duplicate commit caused no second settlement", () -> {
                        assertThat(sourceAfterSecondCommit.debitBalance()
                                        .subtract(sourceBefore.debitBalance()))
                                        .isEqualByComparingTo(amount);

                        assertThat(destinationAfterSecondCommit.creditBalance()
                                        .subtract(destinationBefore.creditBalance()))
                                        .isEqualByComparingTo(amount);

                        assertThat(sourceAfterSecondCommit.balance()
                                        .subtract(sourceBefore.balance()))
                                        .isEqualByComparingTo(amount.negate());

                        assertThat(destinationAfterSecondCommit.balance()
                                        .subtract(destinationBefore.balance()))
                                        .isEqualByComparingTo(amount);
                });
        }

        @Test
        @Tag("LIF-05")
        @Story("LIF-05 - Void inflight transaction")
        @DisplayName("should void an inflight transaction, release its reservation, and prevent settlement")
        void shouldVoidInflightTransactionWithoutSettlement() {
                long fundingAmount = 20_000;
                long transferAmount = 9_500;
                String currency = "NGN";
                int precision = 100;
                long overdraftLimit = 0;
                BigDecimal amount = BigDecimal.valueOf(transferAmount);

                CreateLedgerResponse ledger = createLedger(TestData.unique("lif-05-ledger"));
                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);

                CreateTransactionResponse fundingTransaction = fundBalanceFromWorld(
                                world.balanceId(),
                                source.balanceId(),
                                fundingAmount,
                                currency,
                                overdraftLimit,
                                precision);

                Allure.step("Verify funding transaction was applied", () -> {
                        assertThat(fundingTransaction.status())
                                        .isEqualTo("APPLIED");
                });

                BalanceRecord sourceBefore = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify initial accounting state", () -> {
                        assertThat(sourceBefore.creditBalance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(sourceBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(sourceBefore.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(destinationBefore.creditBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.debitBalance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationBefore.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(sourceBefore);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(destinationBefore);
                });

                CreateTransactionResponse inflightTransferResponse = makeTransfer(
                                source.balanceId(),
                                destination.balanceId(),
                                transferAmount,
                                currency,
                                precision,
                                false,
                                overdraftLimit,
                                true,
                                true);

                String inflightTransactionId = inflightTransferResponse.transactionId();

                Allure.step("Verify transaction entered INFLIGHT state", () -> {
                        assertThat(inflightTransferResponse.status())
                                        .isEqualTo("INFLIGHT");
                });

                BalanceRecord sourceInflight = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationInflight = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify source reservation increased by the transfer amount", () -> {
                        assertThat(sourceInflight.inflightDebitBalance())
                                        .isEqualByComparingTo(
                                                        sourceBefore.inflightDebitBalance().add(amount));

                        assertThat(sourceInflight.inflightCreditBalance())
                                        .isEqualByComparingTo(
                                                        sourceBefore.inflightCreditBalance());
                });

                Allure.step("Verify inflight transfer did not modify settled balances", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceBefore,
                                        sourceInflight);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationBefore,
                                        destinationInflight);
                });

                Response voidResponse = mutateTransactionStatus(
                                inflightTransactionId,
                                TransactionStateMutation.VOID.getValue(),
                                true,
                                null);

                String voidTransactionId = voidResponse.jsonPath()
                                .getString("transaction_id");

                Allure.step("Verify void mutation response", () -> {
                        assertThat(voidResponse.statusCode())
                                        .isBetween(200, 299);

                        assertThat(voidTransactionId)
                                        .isNotBlank();

                        assertThat(voidResponse.jsonPath().getString("status"))
                                        .isEqualTo("VOID");

                        assertThat(voidResponse.jsonPath().getString("parent_transaction"))
                                        .isEqualTo(inflightTransactionId);
                });

                awaitTransactionStatus(voidTransactionId, "VOID");

                TransactionRecord voidTransaction = fetchPersistedTransaction(voidTransactionId);

                Allure.step("Verify persisted void transaction", () -> {
                        assertThat(voidTransaction.status())
                                        .isEqualTo("VOID");

                        assertThat(voidTransaction.parentTransaction())
                                        .isEqualTo(inflightTransactionId);

                        assertThat(voidTransaction.preciseAmount())
                                        .isEqualByComparingTo(amount);
                });

                BalanceRecord sourceAfterVoid = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationAfterVoid = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify source inflight reservation was completely released", () -> {
                        assertThat(sourceAfterVoid.inflightDebitBalance())
                                        .isEqualByComparingTo(sourceBefore.inflightDebitBalance());

                        assertThat(sourceAfterVoid.inflightCreditBalance())
                                        .isEqualByComparingTo(sourceBefore.inflightCreditBalance());
                });

                Allure.step("Verify destination inflight reservation was completely released", () -> {
                        assertThat(destinationAfterVoid.inflightDebitBalance())
                                        .isEqualByComparingTo(destinationBefore.inflightDebitBalance());

                        assertThat(destinationAfterVoid.inflightCreditBalance())
                                        .isEqualByComparingTo(destinationBefore.inflightCreditBalance());
                });

                Allure.step("Verify source settled balances remained unchanged after void", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceBefore,
                                        sourceAfterVoid);
                });

                Allure.step("Verify destination settled balances remained unchanged after void", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationBefore,
                                        destinationAfterVoid);
                });

                Allure.step("Verify no debit or credit was settled", () -> {
                        assertThat(sourceAfterVoid.debitBalance()
                                        .subtract(sourceBefore.debitBalance()))
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(sourceAfterVoid.creditBalance()
                                        .subtract(sourceBefore.creditBalance()))
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationAfterVoid.debitBalance()
                                        .subtract(destinationBefore.debitBalance()))
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationAfterVoid.creditBalance()
                                        .subtract(destinationBefore.creditBalance()))
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                Allure.step("Verify INV-001 after void", () -> {
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        sourceAfterVoid);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        destinationAfterVoid);
                });

                Allure.step("Verify INV-005: void releases reservation without settlement", () -> {
                        assertThat(sourceInflight.inflightDebitBalance()
                                        .subtract(sourceAfterVoid.inflightDebitBalance()))
                                        .isEqualByComparingTo(amount);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceBefore,
                                        sourceAfterVoid);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationBefore,
                                        destinationAfterVoid);
                });
        }

        @Test
        @Tag("LIF-06")
        @Story("LIF-06 - Commit prevention after void")
        @DisplayName("should reject committing a voided transaction and preserve its voided state")
        void shouldRejectCommitAfterVoid() {
                long fundingAmount = 20_000;
                long transferAmount = 9_500;
                String currency = "NGN";
                int precision = 100;
                long overdraftLimit = 0;
                BigDecimal amount = BigDecimal.valueOf(transferAmount);

                CreateLedgerResponse ledger = createLedger(TestData.unique("lif-06-ledger"));
                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);

                CreateTransactionResponse fundingTransaction = fundBalanceFromWorld(
                                world.balanceId(),
                                source.balanceId(),
                                fundingAmount,
                                currency,
                                overdraftLimit,
                                precision);

                Allure.step("Verify funding transaction was applied", () -> {
                        assertThat(fundingTransaction.status()).isEqualTo("APPLIED");
                });

                BalanceRecord sourceBefore = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify initial accounting state", () -> {
                        assertThat(sourceBefore.balance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

                        assertThat(destinationBefore.balance())
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(sourceBefore);
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(destinationBefore);
                });

                CreateTransactionResponse inflightTransferResponse = makeTransfer(
                                source.balanceId(),
                                destination.balanceId(),
                                transferAmount,
                                currency,
                                precision,
                                false,
                                overdraftLimit,
                                true,
                                true);

                String inflightTransactionId = inflightTransferResponse.transactionId();

                Allure.step("Verify transaction entered INFLIGHT state", () -> {
                        assertThat(inflightTransferResponse.status())
                                        .isEqualTo("INFLIGHT");
                });

                BalanceRecord sourceInflight = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationInflight = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify reservation was created without settlement", () -> {
                        assertThat(sourceInflight.inflightDebitBalance())
                                        .isEqualByComparingTo(
                                                        sourceBefore.inflightDebitBalance().add(amount));

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceBefore,
                                        sourceInflight);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationBefore,
                                        destinationInflight);
                });

                Response voidResponse = mutateTransactionStatus(
                                inflightTransactionId,
                                TransactionStateMutation.VOID.getValue(),
                                true,
                                null);

                String voidTransactionId = voidResponse.jsonPath()
                                .getString("transaction_id");

                Allure.step("Verify void mutation succeeded", () -> {
                        assertThat(voidResponse.statusCode())
                                        .isBetween(200, 299);

                        assertThat(voidTransactionId)
                                        .isNotBlank();

                        assertThat(voidResponse.jsonPath().getString("status"))
                                        .isEqualTo("VOID");

                        assertThat(voidResponse.jsonPath().getString("parent_transaction"))
                                        .isEqualTo(inflightTransactionId);
                });

                awaitTransactionStatus(voidTransactionId, "VOID");

                TransactionRecord voidTransaction = fetchPersistedTransaction(voidTransactionId);

                Allure.step("Verify persisted void transaction", () -> {
                        assertThat(voidTransaction.status())
                                        .isEqualTo("VOID");

                        assertThat(voidTransaction.parentTransaction())
                                        .isEqualTo(inflightTransactionId);

                        assertThat(voidTransaction.preciseAmount())
                                        .isEqualByComparingTo(amount);
                });

                BalanceRecord sourceAfterVoid = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationAfterVoid = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify void released the reservation", () -> {
                        assertThat(sourceAfterVoid.inflightDebitBalance())
                                        .isEqualByComparingTo(sourceBefore.inflightDebitBalance());

                        assertThat(sourceAfterVoid.inflightCreditBalance())
                                        .isEqualByComparingTo(sourceBefore.inflightCreditBalance());

                        assertThat(destinationAfterVoid.inflightDebitBalance())
                                        .isEqualByComparingTo(destinationBefore.inflightDebitBalance());

                        assertThat(destinationAfterVoid.inflightCreditBalance())
                                        .isEqualByComparingTo(destinationBefore.inflightCreditBalance());
                });

                Allure.step("Verify void did not settle any funds", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceBefore,
                                        sourceAfterVoid);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationBefore,
                                        destinationAfterVoid);
                });

                Response rejectedCommitResponse = mutateTransactionStatus(
                                inflightTransactionId,
                                TransactionStateMutation.COMMIT.getValue(),
                                true,
                                null);

                attachJson("LIF-06 Commit After Void Rejection", rejectedCommitResponse);

                Allure.step("Verify commit after void is rejected", () -> {
                        assertThat(rejectedCommitResponse.statusCode())
                                        .as("Committing an already voided transaction must be rejected")
                                        .isBetween(400, 499);

                        assertThat(rejectedCommitResponse.jsonPath().getString("error_detail.code"))
                                        .as("A transaction already voided must not be committed")
                                        .isNotBlank();
                });

                BalanceRecord sourceAfterRejectedCommit = fetchPersistedBalance(source.balanceId());

                BalanceRecord destinationAfterRejectedCommit = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify rejected commit did not modify source settled balances", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceAfterVoid,
                                        sourceAfterRejectedCommit);
                });

                Allure.step("Verify rejected commit did not modify destination settled balances", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationAfterVoid,
                                        destinationAfterRejectedCommit);
                });

                Allure.step("Verify rejected commit did not recreate source reservation", () -> {
                        assertThat(sourceAfterRejectedCommit.inflightDebitBalance())
                                        .isEqualByComparingTo(sourceAfterVoid.inflightDebitBalance());

                        assertThat(sourceAfterRejectedCommit.inflightCreditBalance())
                                        .isEqualByComparingTo(sourceAfterVoid.inflightCreditBalance());
                });

                Allure.step("Verify rejected commit did not recreate destination reservation", () -> {
                        assertThat(destinationAfterRejectedCommit.inflightDebitBalance())
                                        .isEqualByComparingTo(destinationAfterVoid.inflightDebitBalance());

                        assertThat(destinationAfterRejectedCommit.inflightCreditBalance())
                                        .isEqualByComparingTo(destinationAfterVoid.inflightCreditBalance());
                });

                Allure.step("Verify void transaction remains VOID after rejected commit", () -> {
                        TransactionRecord persistedVoid = fetchPersistedTransaction(voidTransactionId);

                        assertThat(persistedVoid.status())
                                        .isEqualTo("VOID");

                        assertThat(persistedVoid.parentTransaction())
                                        .isEqualTo(inflightTransactionId);

                        assertThat(persistedVoid.preciseAmount())
                                        .isEqualByComparingTo(amount);
                });

                Allure.step("Verify no financial settlement occurred", () -> {
                        assertThat(sourceAfterRejectedCommit.debitBalance()
                                        .subtract(sourceBefore.debitBalance()))
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(sourceAfterRejectedCommit.creditBalance()
                                        .subtract(sourceBefore.creditBalance()))
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationAfterRejectedCommit.debitBalance()
                                        .subtract(destinationBefore.debitBalance()))
                                        .isEqualByComparingTo(BigDecimal.ZERO);

                        assertThat(destinationAfterRejectedCommit.creditBalance()
                                        .subtract(destinationBefore.creditBalance()))
                                        .isEqualByComparingTo(BigDecimal.ZERO);
                });

                Allure.step("Verify INV-001 after rejected commit", () -> {
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        sourceAfterRejectedCommit);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        destinationAfterRejectedCommit);
                });

                Allure.step("Verify INV-005 and INV-011: voided funds remain unsettled", () -> {
                        assertThat(sourceInflight.inflightDebitBalance()
                                        .subtract(sourceAfterRejectedCommit.inflightDebitBalance()))
                                        .isEqualByComparingTo(amount);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceBefore,
                                        sourceAfterRejectedCommit);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationBefore,
                                        destinationAfterRejectedCommit);

                        assertThat(fetchPersistedTransaction(voidTransactionId).status())
                                        .isEqualTo("VOID");
                });
        }

        @Test
        @Tag("LIF-07")
        @Story("LIF-07 - Void prevention after commit")
        @DisplayName("should reject voiding a committed transaction and preserve its settlement")
        void shouldRejectVoidAfterCommit() {
                long fundingAmount = 20_000;
                long transferAmount = 9_500;
                String currency = "NGN";
                int precision = 100;
                long overdraftLimit = 0;
                BigDecimal amount = BigDecimal.valueOf(transferAmount);

                CreateLedgerResponse ledger = createLedger(TestData.unique("lif-07-ledger"));
                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);

                CreateTransactionResponse fundingTransaction = fundBalanceFromWorld(
                                world.balanceId(),
                                source.balanceId(),
                                fundingAmount,
                                currency,
                                overdraftLimit,
                                precision);

                Allure.step("Verify funding transaction was applied", () -> {
                        assertThat(fundingTransaction.status()).isEqualTo("APPLIED");
                });

                BalanceRecord sourceBefore = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

                CreateTransactionResponse inflightTransfer = makeTransfer(
                                source.balanceId(),
                                destination.balanceId(),
                                transferAmount,
                                currency,
                                precision,
                                false,
                                overdraftLimit,
                                true,
                                true);

                String inflightTransactionId = inflightTransfer.transactionId();

                Allure.step("Verify transaction entered INFLIGHT state", () -> {
                        assertThat(inflightTransfer.status()).isEqualTo("INFLIGHT");
                });

                BalanceRecord sourceInflight = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationInflight = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify reservation exists without settlement", () -> {
                        assertThat(sourceInflight.inflightDebitBalance())
                                        .isEqualByComparingTo(
                                                        sourceBefore.inflightDebitBalance().add(amount));

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceBefore,
                                        sourceInflight);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationBefore,
                                        destinationInflight);
                });

                Response commitResponse = mutateTransactionStatus(
                                inflightTransactionId,
                                TransactionStateMutation.COMMIT.getValue(),
                                true,
                                null);

                String committedTransactionId = commitResponse.jsonPath()
                                .getString("transaction_id");

                Allure.step("Verify commit succeeded", () -> {
                        assertThat(commitResponse.statusCode()).isBetween(200, 299);
                        assertThat(committedTransactionId).isNotBlank();

                        assertThat(commitResponse.jsonPath().getString("status"))
                                        .isEqualTo("APPLIED");

                        assertThat(commitResponse.jsonPath().getString("parent_transaction"))
                                        .isEqualTo(inflightTransactionId);
                });

                awaitTransactionStatus(committedTransactionId, "APPLIED");

                TransactionRecord committedTransaction = fetchPersistedTransaction(committedTransactionId);

                Allure.step("Verify committed child transaction", () -> {
                        assertThat(committedTransaction.status()).isEqualTo("APPLIED");

                        assertThat(committedTransaction.parentTransaction())
                                        .isEqualTo(inflightTransactionId);

                        assertThat(committedTransaction.preciseAmount())
                                        .isEqualByComparingTo(amount);
                });

                BalanceRecord sourceAfterCommit = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationAfterCommit = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify exact settlement and reservation release", () -> {
                        assertThat(sourceAfterCommit.debitBalance())
                                        .isEqualByComparingTo(sourceBefore.debitBalance().add(amount));

                        assertThat(destinationAfterCommit.creditBalance())
                                        .isEqualByComparingTo(destinationBefore.creditBalance().add(amount));

                        assertThat(sourceAfterCommit.balance())
                                        .isEqualByComparingTo(sourceBefore.balance().subtract(amount));

                        assertThat(destinationAfterCommit.balance())
                                        .isEqualByComparingTo(destinationBefore.balance().add(amount));

                        assertThat(sourceAfterCommit.inflightDebitBalance())
                                        .isEqualByComparingTo(sourceBefore.inflightDebitBalance());

                        assertThat(destinationAfterCommit.inflightCreditBalance())
                                        .isEqualByComparingTo(destinationBefore.inflightCreditBalance());
                });

                LedgerInvariantAssertions.assertCommitSettlesInflightReservationExactlyOnce(
                                sourceBefore,
                                destinationBefore,
                                sourceInflight,
                                destinationInflight,
                                sourceAfterCommit,
                                destinationAfterCommit,
                                amount);

                Response rejectedVoidResponse = mutateTransactionStatus(
                                inflightTransactionId,
                                TransactionStateMutation.VOID.getValue(),
                                true,
                                null);

                attachJson("LIF-07 Void After Commit Rejection", rejectedVoidResponse);

                Allure.step("Verify void after commit is rejected", () -> {
                        assertThat(rejectedVoidResponse.statusCode())
                                        .as("Voiding an already committed transaction must be rejected")
                                        .isBetween(400, 499);

                        assertThat(rejectedVoidResponse.jsonPath().getString("error_detail.code"))
                                        .as("Expected an error code for an already committed transaction")
                                        .isNotBlank();
                });

                BalanceRecord sourceAfterRejectedVoid = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationAfterRejectedVoid = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify rejected void did not reverse or duplicate settlement", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceAfterCommit,
                                        sourceAfterRejectedVoid);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationAfterCommit,
                                        destinationAfterRejectedVoid);
                });

                Allure.step("Verify rejected void did not change inflight reservations", () -> {
                        assertThat(sourceAfterRejectedVoid.inflightDebitBalance())
                                        .isEqualByComparingTo(sourceAfterCommit.inflightDebitBalance());

                        assertThat(sourceAfterRejectedVoid.inflightCreditBalance())
                                        .isEqualByComparingTo(sourceAfterCommit.inflightCreditBalance());

                        assertThat(destinationAfterRejectedVoid.inflightDebitBalance())
                                        .isEqualByComparingTo(destinationAfterCommit.inflightDebitBalance());

                        assertThat(destinationAfterRejectedVoid.inflightCreditBalance())
                                        .isEqualByComparingTo(destinationAfterCommit.inflightCreditBalance());
                });

                Allure.step("Verify committed transaction remains APPLIED", () -> {
                        TransactionRecord persistedCommit = fetchPersistedTransaction(committedTransactionId);

                        assertThat(persistedCommit.status()).isEqualTo("APPLIED");

                        assertThat(persistedCommit.parentTransaction())
                                        .isEqualTo(inflightTransactionId);

                        assertThat(persistedCommit.preciseAmount())
                                        .isEqualByComparingTo(amount);
                });

                Allure.step("Verify INV-001 and INV-011 after rejected void", () -> {
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        sourceAfterRejectedVoid);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        destinationAfterRejectedVoid);

                        assertThat(sourceAfterRejectedVoid.debitBalance()
                                        .subtract(sourceBefore.debitBalance()))
                                        .isEqualByComparingTo(amount);

                        assertThat(destinationAfterRejectedVoid.creditBalance()
                                        .subtract(destinationBefore.creditBalance()))
                                        .isEqualByComparingTo(amount);
                });
        }

        @Test
        @Tag("LIF-08")
        @Story("LIF-08 - Reject lifecycle mutations on non-inflight transactions")
        @DisplayName("should reject commit and void on an already applied transaction without financial mutation")
        void shouldRejectCommitAndVoidOnNonInflightTransaction() {
                long fundingAmount = 20_000;
                long transferAmount = 9_500;
                String currency = "NGN";
                int precision = 100;
                long overdraftLimit = 0;
                BigDecimal amount = BigDecimal.valueOf(transferAmount);

                CreateLedgerResponse ledger = createLedger(TestData.unique("lif-08-ledger"));
                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);

                CreateTransactionResponse fundingTransaction = fundBalanceFromWorld(
                                world.balanceId(),
                                source.balanceId(),
                                fundingAmount,
                                currency,
                                overdraftLimit,
                                precision);

                Allure.step("Verify funding transaction was applied", () -> {
                        assertThat(fundingTransaction.status()).isEqualTo("APPLIED");
                });

                BalanceRecord sourceBefore = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

                CreateTransactionResponse appliedTransfer = makeTransfer(
                                source.balanceId(),
                                destination.balanceId(),
                                transferAmount,
                                currency,
                                precision,
                                false,
                                overdraftLimit,
                                true,
                                false);

                String appliedTransactionId = appliedTransfer.transactionId();

                Allure.step("Verify direct transfer was APPLIED without inflight state", () -> {
                        assertThat(appliedTransfer.status()).isEqualTo("APPLIED");
                });

                awaitTransactionStatus(appliedTransactionId, "APPLIED");

                TransactionRecord appliedTransaction = fetchPersistedTransaction(appliedTransactionId);

                Allure.step("Verify persisted transaction is APPLIED", () -> {
                        assertThat(appliedTransaction.status()).isEqualTo("APPLIED");

                        assertThat(appliedTransaction.preciseAmount())
                                        .isEqualByComparingTo(amount);
                });

                BalanceRecord sourceAfterTransfer = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationAfterTransfer = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify direct transfer settled exactly once", () -> {
                        assertThat(sourceAfterTransfer.debitBalance())
                                        .isEqualByComparingTo(sourceBefore.debitBalance().add(amount));

                        assertThat(destinationAfterTransfer.creditBalance())
                                        .isEqualByComparingTo(destinationBefore.creditBalance().add(amount));

                        assertThat(sourceAfterTransfer.balance())
                                        .isEqualByComparingTo(sourceBefore.balance().subtract(amount));

                        assertThat(destinationAfterTransfer.balance())
                                        .isEqualByComparingTo(destinationBefore.balance().add(amount));
                });

                Allure.step("Verify direct transfer created no inflight reservation", () -> {
                        assertThat(sourceAfterTransfer.inflightDebitBalance())
                                        .isEqualByComparingTo(sourceBefore.inflightDebitBalance());

                        assertThat(sourceAfterTransfer.inflightCreditBalance())
                                        .isEqualByComparingTo(sourceBefore.inflightCreditBalance());

                        assertThat(destinationAfterTransfer.inflightDebitBalance())
                                        .isEqualByComparingTo(destinationBefore.inflightDebitBalance());

                        assertThat(destinationAfterTransfer.inflightCreditBalance())
                                        .isEqualByComparingTo(destinationBefore.inflightCreditBalance());
                });

                Response rejectedCommitResponse = mutateTransactionStatus(
                                appliedTransactionId,
                                TransactionStateMutation.COMMIT.getValue(),
                                true,
                                null);

                attachJson("LIF-08 Commit Applied Transaction Rejection", rejectedCommitResponse);

                Allure.step("Verify commit on an APPLIED transaction is rejected", () -> {
                        assertThat(rejectedCommitResponse.statusCode())
                                        .as("A non-inflight transaction must not be committed")
                                        .isBetween(400, 499);

                        assertThat(rejectedCommitResponse.jsonPath().getString("error_detail.code"))
                                        .isNotBlank();
                });

                BalanceRecord sourceAfterRejectedCommit = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationAfterRejectedCommit = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify rejected commit caused no financial mutation", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceAfterTransfer,
                                        sourceAfterRejectedCommit);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationAfterTransfer,
                                        destinationAfterRejectedCommit);

                        assertThat(sourceAfterRejectedCommit.inflightDebitBalance())
                                        .isEqualByComparingTo(sourceAfterTransfer.inflightDebitBalance());

                        assertThat(destinationAfterRejectedCommit.inflightCreditBalance())
                                        .isEqualByComparingTo(destinationAfterTransfer.inflightCreditBalance());
                });

                Response rejectedVoidResponse = mutateTransactionStatus(
                                appliedTransactionId,
                                TransactionStateMutation.VOID.getValue(),
                                true,
                                null);

                attachJson("LIF-08 Void Applied Transaction Rejection", rejectedVoidResponse);

                Allure.step("Verify void on an APPLIED transaction is rejected", () -> {
                        assertThat(rejectedVoidResponse.statusCode())
                                        .as("A non-inflight transaction must not be voided")
                                        .isBetween(400, 499);

                        assertThat(rejectedVoidResponse.jsonPath().getString("error_detail.code"))
                                        .isNotBlank();
                });

                BalanceRecord sourceAfterRejectedVoid = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationAfterRejectedVoid = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify rejected void caused no financial mutation", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        sourceAfterTransfer,
                                        sourceAfterRejectedVoid);

                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                        destinationAfterTransfer,
                                        destinationAfterRejectedVoid);
                });

                Allure.step("Verify neither rejected mutation changed reservations", () -> {
                        assertThat(sourceAfterRejectedVoid.inflightDebitBalance())
                                        .isEqualByComparingTo(sourceAfterTransfer.inflightDebitBalance());

                        assertThat(sourceAfterRejectedVoid.inflightCreditBalance())
                                        .isEqualByComparingTo(sourceAfterTransfer.inflightCreditBalance());

                        assertThat(destinationAfterRejectedVoid.inflightDebitBalance())
                                        .isEqualByComparingTo(destinationAfterTransfer.inflightDebitBalance());

                        assertThat(destinationAfterRejectedVoid.inflightCreditBalance())
                                        .isEqualByComparingTo(destinationAfterTransfer.inflightCreditBalance());
                });

                Allure.step("Verify original transaction remains APPLIED", () -> {
                        TransactionRecord persistedTransaction = fetchPersistedTransaction(appliedTransactionId);

                        assertThat(persistedTransaction.status()).isEqualTo("APPLIED");

                        assertThat(persistedTransaction.preciseAmount())
                                        .isEqualByComparingTo(amount);
                });

                Allure.step("Verify INV-001 and INV-011", () -> {
                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        sourceAfterRejectedVoid);

                        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                                        destinationAfterRejectedVoid);

                        assertThat(sourceAfterRejectedVoid.debitBalance()
                                        .subtract(sourceBefore.debitBalance()))
                                        .isEqualByComparingTo(amount);

                        assertThat(destinationAfterRejectedVoid.creditBalance()
                                        .subtract(destinationBefore.creditBalance()))
                                        .isEqualByComparingTo(amount);
                });
        }

        @Test
        @Tag("LIF-09")
        @Story("LIF-09 - Unknown inflight transaction mutation")
        @DisplayName("should return HTTP 404 when committing or voiding an unknown transaction")
        void shouldRejectMutationOfUnknownInflightTransaction() {
                String unknownTransactionId = UUID.randomUUID().toString();

                for (TransactionStateMutation mutation : List.of(
                                TransactionStateMutation.COMMIT,
                                TransactionStateMutation.VOID)) {

                        Response response = mutateTransactionStatus(
                                        unknownTransactionId,
                                        mutation.getValue(),
                                        true,
                                        null);

                        Allure.step("Verify unknown transaction rejects " + mutation.getValue(), () -> {
                                assertThat(response.statusCode()).isEqualTo(400);
                        });
                }
        }

        @Test
@Tag("LIF-11")
@Story("LIF-11 - Partial commit followed by remainder settlement")
@DisplayName("should settle the original inflight amount exactly once across partial commits")
void shouldCommitRemainingAmountWithoutExceedingOriginalReservation() {
    long fundingAmount = 20_000;
    long transferAmount = 10_000;
    long firstCommitAmount = 4_000;
    long remainingCommitAmount = transferAmount - firstCommitAmount;
    String currency = "NGN";
    int precision = 100;
    long overdraftLimit = 0;

    BigDecimal totalAmount = BigDecimal.valueOf(transferAmount);
    BigDecimal firstAmount = BigDecimal.valueOf(firstCommitAmount);
    BigDecimal remainingAmount = BigDecimal.valueOf(remainingCommitAmount);

    CreateLedgerResponse ledger = createLedger(TestData.unique("lif-11-ledger"));
    CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);
    CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);
    CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);

    CreateTransactionResponse fundingTransaction = fundBalanceFromWorld(
            world.balanceId(),
            source.balanceId(),
            fundingAmount,
            currency,
            overdraftLimit,
            precision
    );

    Allure.step("Verify funding transaction was applied", () -> {
        assertThat(fundingTransaction.status()).isEqualTo("APPLIED");
    });

    BalanceRecord sourceBefore = fetchPersistedBalance(source.balanceId());
    BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

    CreateTransactionResponse inflightTransfer = makeTransfer(
            source.balanceId(),
            destination.balanceId(),
            transferAmount,
            currency,
            precision,
            false,
            overdraftLimit,
            true,
            true
    );

    String inflightTransactionId = inflightTransfer.transactionId();

    Allure.step("Verify original transaction entered INFLIGHT state", () -> {
        assertThat(inflightTransfer.status()).isEqualTo("INFLIGHT");
    });

    BalanceRecord sourceInflight = fetchPersistedBalance(source.balanceId());
    BalanceRecord destinationInflight = fetchPersistedBalance(destination.balanceId());

    Allure.step("Verify full reservation exists without settlement", () -> {
        assertThat(sourceInflight.inflightDebitBalance())
                .isEqualByComparingTo(
                        sourceBefore.inflightDebitBalance().add(totalAmount)
                );

        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                sourceBefore,
                sourceInflight
        );

        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                destinationBefore,
                destinationInflight
        );
    });

    Response firstCommitResponse = mutateTransactionStatus(
            inflightTransactionId,
            TransactionStateMutation.COMMIT.getValue(),
            true,
            firstCommitAmount
    );

    Allure.step("Verify first partial commit succeeded", () -> {
        assertThat(firstCommitResponse.statusCode()).isBetween(200, 299);

        assertThat(firstCommitResponse.jsonPath().getString("status"))
                .isEqualTo("APPLIED");

        assertThat(firstCommitResponse.jsonPath().getString("parent_transaction"))
                .isEqualTo(inflightTransactionId);
    });

    String firstChildId = firstCommitResponse.jsonPath()
            .getString("transaction_id");

    assertThat(firstChildId).isNotBlank();

    awaitTransactionStatus(firstChildId, "APPLIED");

    TransactionRecord firstChild = fetchPersistedTransaction(firstChildId);

    Allure.step("Verify first settlement child", () -> {
        assertThat(firstChild.status()).isEqualTo("APPLIED");

        assertThat(firstChild.parentTransaction())
                .isEqualTo(inflightTransactionId);

        assertThat(firstChild.preciseAmount())
                .isEqualByComparingTo(firstAmount);
    });

    BalanceRecord sourceAfterFirstCommit = fetchPersistedBalance(source.balanceId());
    BalanceRecord destinationAfterFirstCommit = fetchPersistedBalance(destination.balanceId());

    Allure.step("Verify only the first partial amount was settled", () -> {
        assertThat(sourceAfterFirstCommit.debitBalance())
                .isEqualByComparingTo(
                        sourceBefore.debitBalance().add(firstAmount)
                );

        assertThat(destinationAfterFirstCommit.creditBalance())
                .isEqualByComparingTo(
                        destinationBefore.creditBalance().add(firstAmount)
                );

        assertThat(sourceAfterFirstCommit.balance())
                .isEqualByComparingTo(
                        sourceBefore.balance().subtract(firstAmount)
                );

        assertThat(destinationAfterFirstCommit.balance())
                .isEqualByComparingTo(
                        destinationBefore.balance().add(firstAmount)
                );
    });

    Allure.step("Verify remaining reservation is available", () -> {
        assertThat(sourceAfterFirstCommit.inflightDebitBalance())
                .isEqualByComparingTo(
                        sourceBefore.inflightDebitBalance().add(remainingAmount)
                );
    });

    Response secondCommitResponse = mutateTransactionStatus(
            inflightTransactionId,
            TransactionStateMutation.COMMIT.getValue(),
            true,
            remainingCommitAmount
    );

    Allure.step("Verify remainder commit succeeded", () -> {
        assertThat(secondCommitResponse.statusCode()).isBetween(200, 299);

        assertThat(secondCommitResponse.jsonPath().getString("status"))
                .isEqualTo("APPLIED");

        assertThat(secondCommitResponse.jsonPath().getString("parent_transaction"))
                .isEqualTo(inflightTransactionId);
    });

    String secondChildId = secondCommitResponse.jsonPath()
            .getString("transaction_id");

    assertThat(secondChildId)
            .isNotBlank()
            .isNotEqualTo(firstChildId);

    awaitTransactionStatus(secondChildId, "APPLIED");

    TransactionRecord secondChild = fetchPersistedTransaction(secondChildId);

    Allure.step("Verify remainder settlement child", () -> {
        assertThat(secondChild.status()).isEqualTo("APPLIED");

        assertThat(secondChild.parentTransaction())
                .isEqualTo(inflightTransactionId);

        assertThat(secondChild.preciseAmount())
                .isEqualByComparingTo(remainingAmount);
    });

    BalanceRecord sourceAfterSecondCommit = fetchPersistedBalance(source.balanceId());
    BalanceRecord destinationAfterSecondCommit = fetchPersistedBalance(destination.balanceId());

    Allure.step("Verify total settled amount equals original transfer amount", () -> {
        assertThat(firstChild.preciseAmount().add(secondChild.preciseAmount()))
                .isEqualByComparingTo(totalAmount);

        assertThat(sourceAfterSecondCommit.debitBalance())
                .isEqualByComparingTo(
                        sourceBefore.debitBalance().add(totalAmount)
                );

        assertThat(destinationAfterSecondCommit.creditBalance())
                .isEqualByComparingTo(
                        destinationBefore.creditBalance().add(totalAmount)
                );

        assertThat(sourceAfterSecondCommit.balance())
                .isEqualByComparingTo(
                        sourceBefore.balance().subtract(totalAmount)
                );

        assertThat(destinationAfterSecondCommit.balance())
                .isEqualByComparingTo(
                        destinationBefore.balance().add(totalAmount)
                );
    });

    Allure.step("Verify original reservation is fully released", () -> {
        assertThat(sourceAfterSecondCommit.inflightDebitBalance())
                .isEqualByComparingTo(sourceBefore.inflightDebitBalance());

        assertThat(sourceAfterSecondCommit.inflightCreditBalance())
                .isEqualByComparingTo(sourceBefore.inflightCreditBalance());

        assertThat(destinationAfterSecondCommit.inflightDebitBalance())
                .isEqualByComparingTo(destinationBefore.inflightDebitBalance());

        assertThat(destinationAfterSecondCommit.inflightCreditBalance())
                .isEqualByComparingTo(destinationBefore.inflightCreditBalance());
    });

    Response excessCommitResponse = mutateTransactionStatus(
            inflightTransactionId,
            TransactionStateMutation.COMMIT.getValue(),
            true,
            1L
    );

    Allure.step("Verify committing beyond the original amount is rejected", () -> {
        assertThat(excessCommitResponse.statusCode())
                .isBetween(400, 499);

        assertThat(excessCommitResponse.jsonPath().getString("error_detail.code"))
                .isNotBlank();
    });

    BalanceRecord sourceAfterExcessAttempt = fetchPersistedBalance(source.balanceId());
    BalanceRecord destinationAfterExcessAttempt = fetchPersistedBalance(destination.balanceId());

    Allure.step("Verify excess commit caused no additional settlement", () -> {
        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                sourceAfterSecondCommit,
                sourceAfterExcessAttempt
        );

        LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                destinationAfterSecondCommit,
                destinationAfterExcessAttempt
        );

        assertThat(sourceAfterExcessAttempt.inflightDebitBalance())
                .isEqualByComparingTo(
                        sourceAfterSecondCommit.inflightDebitBalance()
                );

        assertThat(destinationAfterExcessAttempt.inflightCreditBalance())
                .isEqualByComparingTo(
                        destinationAfterSecondCommit.inflightCreditBalance()
                );
    });

    Allure.step("Verify both settlement children remain APPLIED", () -> {
        assertThat(fetchPersistedTransaction(firstChildId).status())
                .isEqualTo("APPLIED");

        assertThat(fetchPersistedTransaction(secondChildId).status())
                .isEqualTo("APPLIED");
    });

    Allure.step("Verify INV-001 and INV-004", () -> {
        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                sourceAfterExcessAttempt
        );

        LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                destinationAfterExcessAttempt
        );

        assertThat(sourceAfterExcessAttempt.debitBalance()
                .subtract(sourceBefore.debitBalance()))
                .isEqualByComparingTo(totalAmount);

        assertThat(destinationAfterExcessAttempt.creditBalance()
                .subtract(destinationBefore.creditBalance()))
                .isEqualByComparingTo(totalAmount);
    });
}

        @Test
        @Tag("LIF-12")
        @Story("LIF-12 - Invalid inflight mutation status")
        @DisplayName("should reject invalid mutation statuses without changing transaction or balances")
        void shouldRejectInvalidInflightMutationStatuses() {
                long fundingAmount = 20_000;
                long transferAmount = 9_500;
                String currency = "NGN";
                int precision = 100;
                long overdraftLimit = 0;
                BigDecimal amount = BigDecimal.valueOf(transferAmount);

                CreateLedgerResponse ledger = createLedger(TestData.unique("lif-12-ledger"));
                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);

                fundBalanceFromWorld(
                                world.balanceId(),
                                source.balanceId(),
                                fundingAmount,
                                currency,
                                overdraftLimit,
                                precision);

                CreateTransactionResponse inflightTransfer = makeTransfer(
                                source.balanceId(),
                                destination.balanceId(),
                                transferAmount,
                                currency,
                                precision,
                                false,
                                overdraftLimit,
                                true,
                                true);

                String inflightTransactionId = inflightTransfer.transactionId();

                Allure.step("Verify transaction entered INFLIGHT state", () -> {
                        assertThat(inflightTransfer.status()).isEqualTo("INFLIGHT");
                });

                BalanceRecord sourceBefore = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify reservation exists before invalid mutations", () -> {
                        assertThat(sourceBefore.inflightDebitBalance())
                                        .isEqualByComparingTo(amount);
                });

                for (String invalidStatus : List.of("COMMIT", "foo", "")) {
                        Response response = mutateTransactionStatus(
                                        inflightTransactionId,
                                        invalidStatus,
                                        true,
                                        null);

                        Allure.step("Verify invalid status '" + invalidStatus + "' returns HTTP 400", () -> {
                                assertThat(response.statusCode()).isEqualTo(400);
                        });

                        BalanceRecord sourceAfter = fetchPersistedBalance(source.balanceId());
                        BalanceRecord destinationAfter = fetchPersistedBalance(destination.balanceId());

                        Allure.step("Verify invalid status '" + invalidStatus + "' caused no financial mutation",
                                        () -> {
                                                LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                                                sourceBefore,
                                                                sourceAfter);

                                                LedgerInvariantAssertions.assertSettledBalanceUnchanged(
                                                                destinationBefore,
                                                                destinationAfter);

                                                assertThat(sourceAfter.inflightDebitBalance())
                                                                .isEqualByComparingTo(
                                                                                sourceBefore.inflightDebitBalance());

                                                assertThat(sourceAfter.inflightCreditBalance())
                                                                .isEqualByComparingTo(
                                                                                sourceBefore.inflightCreditBalance());

                                                assertThat(destinationAfter.inflightDebitBalance())
                                                                .isEqualByComparingTo(destinationBefore
                                                                                .inflightDebitBalance());

                                                assertThat(destinationAfter.inflightCreditBalance())
                                                                .isEqualByComparingTo(destinationBefore
                                                                                .inflightCreditBalance());

                                                LedgerInvariantAssertions.assertDerivedBalanceInvariant(sourceAfter);
                                                LedgerInvariantAssertions
                                                                .assertDerivedBalanceInvariant(destinationAfter);
                                        });

                        Allure.step("Verify original transaction remains INFLIGHT", () -> {
                                assertThat(fetchPersistedTransaction(inflightTransactionId).status())
                                                .isEqualTo("INFLIGHT");
                        });
                }
        }
}
