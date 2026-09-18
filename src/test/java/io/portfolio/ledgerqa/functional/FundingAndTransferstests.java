package io.portfolio.ledgerqa.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.portfolio.ledgerqa.db.model.BalanceRecord;
import io.portfolio.ledgerqa.model.responses.CreateBalanceResponse;
import io.portfolio.ledgerqa.model.responses.CreateTransactionResponse;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.assertions.LedgerInvariantAssertions;

import io.portfolio.ledgerqa.testsupport.TestData;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

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

                Allure.step("Verify Recipient INV-001", () -> LedgerInvariantAssertions.assertDerivedBalanceInvariant(recipientAfter));

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

                Allure.step("Verify Soiurce INV-001", () -> LedgerInvariantAssertions.assertDerivedBalanceInvariant(worldAfter));

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

                Allure.step("Verify balances", () -> {
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
}
