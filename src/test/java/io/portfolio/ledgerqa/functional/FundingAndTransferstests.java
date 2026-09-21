package io.portfolio.ledgerqa.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.portfolio.ledgerqa.db.model.BalanceRecord;
import io.portfolio.ledgerqa.db.model.TransactionRecord;
import io.portfolio.ledgerqa.helpers.Helpers;
import io.portfolio.ledgerqa.model.responses.CreateBalanceResponse;
import io.portfolio.ledgerqa.model.responses.CreateTransactionResponse;
import io.portfolio.ledgerqa.model.responses.FetchTransactionResponse;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.assertions.LedgerInvariantAssertions;

import io.portfolio.ledgerqa.testsupport.TestData;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;

@Epic("Ledger Quality Platform")
@Feature("Balances")
@Tag("functional")
class FundingAndTransferTests extends FunctionalTestBase {

        @Test
        @Tag("FUN-01")
        @Story("FUN-01 - Fund a balance")
        @DisplayName("should increase the destination balance by the exact funded amount")
        void shouldIncreaseDestinationBalanceByExactFundedAmount() {

                long fundingAmount = 10_000;
                String currency = "NGN";
                int precision = 100;

                // Given
                CreateLedgerResponse ledger = createLedger(TestData.unique("fun-01"));

                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse recipient = createBalance(ledger.ledgerId(), currency);

                BalanceRecord recipientBefore = fetchPersistedBalance(recipient.balanceId());

                // When
                CreateTransactionResponse fundingTransaction = fundBalanceFromWorld(
                                world.balanceId(),
                                recipient.balanceId(),
                                fundingAmount,
                                currency,
                                precision);

                BalanceRecord worldAfter = fetchPersistedBalance(world.balanceId());

                // Then
                Allure.step("Verify funding transaction was applied", () -> {
                        assertThat(fundingTransaction.status()).isEqualTo("APPLIED");

                        assertThat(fundingTransaction.preciseAmount()).isEqualTo(fundingAmount);

                        assertThat(worldAfter.balanceId()).isEqualTo(fundingTransaction.source());

                        assertThat(recipient.balanceId()).isEqualTo(fundingTransaction.destination());

                        assertThat(fundingTransaction.allowOverdraft()).isTrue();

                        assertThat(fundingTransaction.skipQueue()).isTrue();
                });

                BalanceRecord recipientAfter = fetchPersistedBalance(recipient.balanceId());

                Allure.step("Verify recipient received the exact funded amount", () -> {

                        assertThat(recipientAfter.creditBalance())
                                        .isEqualByComparingTo(
                                                        recipientBefore.creditBalance()
                                                                        .add(BigDecimal.valueOf(fundingAmount)));

                        assertThat(recipientAfter.balance())
                                        .isEqualByComparingTo(
                                                        recipientBefore.balance()
                                                                        .add(BigDecimal.valueOf(fundingAmount)));

                        assertThat(recipientAfter.debitBalance())
                                        .isEqualByComparingTo(
                                                        recipientBefore.debitBalance());
                });

                Allure.step("Verify Recipient INV-001",
                                () -> LedgerInvariantAssertions.assertDerivedBalanceInvariant(recipientAfter));

                BigDecimal negativeFundingAmount = BigDecimal.valueOf(-fundingAmount);
                assertThat(worldAfter.balance()).isEqualTo(negativeFundingAmount);

                Allure.step("Verify source was debited the exact funded amount", () -> {

                        // Creditt balancee must remain unchanged
                        assertThat(worldAfter.creditBalance())
                                        .isEqualByComparingTo(BigDecimal.valueOf(world.creditBalance()));

                        // Debit must be increase by the funded amount
                        assertThat(worldAfter.debitBalance())
                                        .isEqualByComparingTo(
                                                        BigDecimal.valueOf(world.debitBalance())
                                                                        .add(BigDecimal.valueOf(fundingAmount)));

                        // Balance must be decrease by the funded amount
                        assertThat(worldAfter.balance())
                                        .isEqualByComparingTo(
                                                        BigDecimal.valueOf(world.balance())
                                                                        .subtract(BigDecimal.valueOf(fundingAmount)));
                });

                Allure.step("Verify Soiurce INV-001",
                                () -> LedgerInvariantAssertions.assertDerivedBalanceInvariant(worldAfter));

        }

        @Test
        @Tag("FUN-02")
        @Story("FUN-02 - Transfer between balances in the same ledger")
        @DisplayName("should transfer the exact amount between two balances in the same ledger")
        void shouldTransferExactAmountBetweenBalancesInSameLedger() {
                long fundingAmount = 7_000;
                long debitAmount = 1_340;
                String currency = "NGN";
                int precision = 100;

                // Given
                FundedBalanceFixture sourceBalanceObject = createFundedBalance(fundingAmount, currency, precision);
                CreateLedgerResponse ledger = sourceBalanceObject.ledger();
                CreateBalanceResponse destinationBalance = createBalance(ledger.ledgerId(), currency);

                // When
                CreateBalanceResponse sourceBalance = sourceBalanceObject.balance();
                CreateTransactionResponse transferResponse = makeTransfer(
                                sourceBalance.balanceId(),
                                destinationBalance.balanceId(),
                                debitAmount, currency, precision,
                                false,
                                true, false);

                // Then fetch databasse persisted data Via SQl queriies
                BalanceRecord sourceBalanceAfter = fetchPersistedBalance(sourceBalance.balanceId());
                BalanceRecord destinationBalanceAfter = fetchPersistedBalance(destinationBalance.balanceId());

                Allure.step("Verify balances and invariant rule 'INV-01'", () -> {
                        assertThat(transferResponse.status()).isEqualTo("APPLIED");
                        // Source debit balance after trnafser increased by debitAmount
                        assertThat(sourceBalanceAfter.debitBalance())
                                        .isEqualTo(BigDecimal.valueOf(sourceBalance.debitBalance() + debitAmount));
                        // Source balance after transfer reduced by debitAmount after transfer
                        assertThat(sourceBalanceAfter.balance())
                                        .isEqualTo(BigDecimal.valueOf(fundingAmount - debitAmount));
                        // Destination debit balance remains unchanhed
                        assertThat(destinationBalanceAfter.debitBalance())
                                        .isEqualTo(BigDecimal.valueOf(destinationBalance.debitBalance()));
                        // Destination credit balance inccreased by the source debited amount after
                        // transfer
                        assertThat(destinationBalanceAfter.creditBalance())
                                        .isEqualTo(BigDecimal
                                                        .valueOf(destinationBalance.creditBalance() + debitAmount));

                        // Destination balance inccreased by the source debited amount after
                        // transfer
                        assertThat(destinationBalanceAfter.balance())
                                        .isEqualTo(BigDecimal
                                                        .valueOf(destinationBalance.balance() + debitAmount));

                });

                // Verify against our first inavatiant law
                Allure.step("Verify Soiurce INV-001",
                                () -> LedgerInvariantAssertions.assertDerivedBalanceInvariant(sourceBalanceAfter));
                Allure.step("Verify destination INV-001",
                                () -> LedgerInvariantAssertions.assertDerivedBalanceInvariant(destinationBalanceAfter));

        }

        @Test
        @Tag("FUN-03")
        @Story("FUN-03 - Transfer across different ledgers with the same currency")
        @DisplayName("should transfer between balances in different ledgers with the same currency")
        void shouldTransferBetweenBalancesInDifferentLedgersWithSameCurrency() {
                long fundingAmount = 8_000;
                long debitAmount = 4_200;
                String currency = "NGN";
                int precision = 100;

                // Given (Create ledger A and balances)
                CreateLedgerResponse ledgerA = createLedger(TestData.unique("fun-03-ledger-a"));

                CreateBalanceResponse world = createBalance(ledgerA.ledgerId(), currency);

                CreateBalanceResponse source = createBalance(ledgerA.ledgerId(), currency);

                CreateTransactionResponse fundingTransaction = fundBalanceFromWorld(world.balanceId(),
                                source.balanceId(), fundingAmount, currency, precision);

                BalanceRecord sourceAfter = fetchPersistedBalance(source.balanceId());

                Allure.step("Verify funding transaction was applied", () -> {
                        assertThat(fundingTransaction.status()).isEqualTo("APPLIED");
                        assertThat(fundingTransaction.preciseAmount()).isEqualTo(fundingAmount);
                        assertThat(sourceAfter.balance()).isEqualTo(BigDecimal.valueOf(fundingAmount));
                });

                // Create ledger B and balances
                CreateLedgerResponse ledgerB = createLedger(TestData.unique("fun-03-ledger-b"));

                CreateBalanceResponse destination = createBalance(ledgerB.ledgerId(), currency);

                BalanceRecord destinationBefore = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify destination Balance in Ledger B is Zero", () -> {
                        assertThat(destinationBefore.balance()).isZero();
                });

                CreateTransactionResponse transferResponse = makeTransfer(
                                source.balanceId(),
                                destination.balanceId(),
                                debitAmount, currency, precision,
                                false,
                                true, false);

                BalanceRecord destinationAfterTransfer = fetchPersistedBalance(destination.balanceId());
                BalanceRecord sourceAfterTransfer = fetchPersistedBalance(source.balanceId());

                Allure.step("Verify source and destination balances", () -> {
                        assertThat(transferResponse.status()).isEqualTo("APPLIED");
                        assertThat(transferResponse.preciseAmount()).isEqualTo(debitAmount);
                        assertThat(transferResponse.source()).isEqualTo(source.balanceId());
                        assertThat(transferResponse.destination()).isEqualTo(destination.balanceId());
                        assertThat(transferResponse.currency()).isEqualTo(currency);
                        assertThat(transferResponse.allowOverdraft()).isFalse();
                });

                Allure.step("Verify source and destination balances", () -> {
                        assertThat(transferResponse.status()).isEqualTo("APPLIED");
                        // Source debit balance after trnafser increased by debitAmount
                        assertThat(sourceAfterTransfer.debitBalance())
                                        .isEqualTo(BigDecimal.valueOf(source.debitBalance() + debitAmount));
                        // Source balance after transfer reduced by debitAmount after transfer
                        assertThat(sourceAfterTransfer.balance())
                                        .isEqualTo(BigDecimal.valueOf(fundingAmount - debitAmount));
                        // Destination debit balance remains unchanhed
                        assertThat(destinationAfterTransfer.debitBalance())
                                        .isEqualTo(BigDecimal.valueOf(destination.debitBalance()));
                        // Destination credit balance inccreased by the source debited amount after
                        // transfer
                        assertThat(destinationAfterTransfer.creditBalance())
                                        .isEqualTo(BigDecimal
                                                        .valueOf(destination.creditBalance() + debitAmount));

                        // Destination balance inccreased by the source debited amount after
                        // transfer
                        assertThat(destinationAfterTransfer.balance())
                                        .isEqualTo(BigDecimal
                                                        .valueOf(destination.balance() + debitAmount));

                });

                // Verify against our first invatiant law
                Allure.step("Verify Soiurce INV-001",
                                () -> LedgerInvariantAssertions.assertDerivedBalanceInvariant(sourceAfterTransfer));
                Allure.step("Verify destination INV-001",
                                () -> LedgerInvariantAssertions
                                                .assertDerivedBalanceInvariant(destinationAfterTransfer));

        }

        @Test
        @Tag("FUN-04")
        @Story("FUN-04 - Zero-amount transaction")
        @DisplayName("should reject a zero-amount transaction without mutating balances")
        void shouldRejectZeroAmountTransactionWithoutMutatingBalances() {
                long fundingAmount = 8_000;
                long debitAmount = 0;
                String currency = "NGN";
                int precision = 100;

                // Given (Create ledger A and balances)
                CreateLedgerResponse ledger = createLedger(TestData.unique("fun-04-ledger"));

                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse source = createBalance(ledger.ledgerId(), currency);
                CreateBalanceResponse destination = createBalance(ledger.ledgerId(), currency);

                CreateTransactionResponse fundingTransaction = fundBalanceFromWorld(world.balanceId(),
                                source.balanceId(), fundingAmount, currency, precision);

                BalanceRecord sourceBalanceBefore = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationBalanceBefore = fetchPersistedBalance(destination.balanceId());

                Allure.step("Verify funding transaction was applied", () -> {
                        assertThat(fundingTransaction.status()).isEqualTo("APPLIED");
                        assertThat(fundingTransaction.preciseAmount()).isEqualTo(fundingAmount);
                        assertThat(sourceBalanceBefore.balance()).isEqualTo(BigDecimal.valueOf(fundingAmount));
                        assertThat(destinationBalanceBefore.balance()).isZero();
                });

                Response transferResponse = makeTransferToFail(
                                source.balanceId(),
                                destination.balanceId(),
                                debitAmount, currency, precision,
                                false,
                                true, false);

                Allure.step("Verify zero-amount transaction was rejected", () -> {
                        assertThat(transferResponse.statusCode())
                                        .isEqualTo(400);

                        assertThat(transferResponse.jsonPath().getString("error_detail.code"))
                                        .isEqualTo("TXN_VALIDATION_ERROR");

                        assertThat(transferResponse.jsonPath().getString("error_detail.message"))
                                        .contains("transaction precise amount must be positive");
                });

                BalanceRecord sourceBalanceAfter = fetchPersistedBalance(source.balanceId());
                BalanceRecord destinationBalanceAfter = fetchPersistedBalance(destination.balanceId());

                Allure.step("INV-02: Verify source and destination balances remain unchanged", () -> {
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(sourceBalanceBefore,
                                        sourceBalanceAfter);
                        LedgerInvariantAssertions.assertSettledBalanceUnchanged(destinationBalanceBefore,
                                        destinationBalanceAfter);
                });

        }

        @Test
        @Tag("FUN-05")
        @Story("FUN-05 - Fetch transaction by ID")
        @DisplayName("should return the final transaction state and match the persisted database record")
        void shouldFetchFinalTransactionStateMatchingDatabase() {
                long fundingAmount = 7_000;
                long debitAmount = 3_500;
                String currency = "NGN";
                int precision = 100;

                // Given
                FundedBalanceFixture sourceBalanceObject = createFundedBalance(fundingAmount, currency, precision);

                CreateLedgerResponse ledger = sourceBalanceObject.ledger();

                CreateBalanceResponse sourceBalance = sourceBalanceObject.balance();

                CreateBalanceResponse destinationBalance = createBalance(ledger.ledgerId(), currency);

                // When
                CreateTransactionResponse transferResponse = makeTransfer(
                                sourceBalance.balanceId(),
                                destinationBalance.balanceId(),
                                debitAmount,
                                currency,
                                precision,
                                false,
                                true,
                                false);

                String transactionId = transferResponse.transactionId();

                TransactionRecord transactionFromDb = fetchPersistedTransaction(transactionId);

                FetchTransactionResponse transactionFromApi = fetchTransactionViaApi(transactionId);

                // Then
                Allure.step("Verify fetched transaction has the expected final state", () -> {

                        assertThat(transactionFromApi.transactionId())
                                        .isEqualTo(transactionId);

                        assertThat(transactionFromApi.status())
                                        .isEqualTo("APPLIED");

                        assertThat(transactionFromApi.source())
                                        .isEqualTo(sourceBalance.balanceId());

                        assertThat(transactionFromApi.destination())
                                        .isEqualTo(destinationBalance.balanceId());

                        assertThat(transactionFromApi.preciseAmount())
                                        .isEqualTo(debitAmount);

                        assertThat(transactionFromApi.currency())
                                        .isEqualTo(currency);

                        assertThat(transactionFromApi.precision())
                                        .isEqualTo(precision);

                        assertThat(transactionFromApi.reference())
                                        .isEqualTo(transferResponse.reference());
                });

                Allure.step("Verify fetched transaction matches persisted database record", () -> {

                        assertThat(transactionFromApi.transactionId())
                                        .isEqualTo(transactionFromDb.transactionId());

                        assertThat(transactionFromApi.source())
                                        .isEqualTo(transactionFromDb.source());

                        assertThat(transactionFromApi.destination())
                                        .isEqualTo(transactionFromDb.destination());

                        assertThat(BigDecimal.valueOf(transactionFromApi.preciseAmount()))
                                        .isEqualTo(transactionFromDb.preciseAmount());

                        assertThat(transactionFromApi.currency())
                                        .isEqualTo(transactionFromDb.currency());

                        assertThat(transactionFromApi.reference())
                                        .isEqualTo(transactionFromDb.reference());

                        assertThat(transactionFromApi.status())
                                        .isEqualTo(transactionFromDb.status());
                });
        }
}
