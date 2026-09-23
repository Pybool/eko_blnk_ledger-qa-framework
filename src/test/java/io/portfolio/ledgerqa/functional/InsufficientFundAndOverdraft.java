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
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.assertions.LedgerInvariantAssertions;

import io.portfolio.ledgerqa.testsupport.TestData;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;

@Epic("Ledger Quality Platform")
@Feature("Balances")
@Tag("functional")
class InsufficientFundAndOverdraft extends FunctionalTestBase {

    @Test
    @Tag("FUN-06")
    @Story("FUN-06 - Insufficient funds with overdraft disabled")
    @DisplayName("should reject transfer exceeding source balance when overdraft is disabled")
    void shouldRejectTransferExceedingSourceBalanceWhenOverdraftIsDisabled() {
        long fundingAmount = 5_000;
        long transferAmount = 7_000;
        String currency = "NGN";
        int precision = 100;

        // Given
        CreateLedgerResponse ledger = createLedger(TestData.unique("fun-06-ledger"));

        CreateBalanceResponse worldBalance = createBalance(ledger.ledgerId(), currency);

        CreateBalanceResponse sourceBalance = createBalance(ledger.ledgerId(), currency);

        CreateBalanceResponse destinationBalance = createBalance(ledger.ledgerId(), currency);

        fundBalanceFromWorld(worldBalance.balanceId(), sourceBalance.balanceId(), fundingAmount, currency,
                precision);

        // Take SQl Query Db Snapshots beffore transfer attempt
        BalanceRecord sourceBeforeTransfer = fetchPersistedBalance(sourceBalance.balanceId());

        BalanceRecord destinationBeforeTransfer = fetchPersistedBalance(destinationBalance.balanceId());

        // When
        Response transferAttemptResponse = makeTransferToFail(
                sourceBalance.balanceId(),
                destinationBalance.balanceId(),
                transferAmount,
                currency,
                precision,
                false,
                true,
                false);

        int statusCode = transferAttemptResponse.getStatusCode();
        String messageCode = transferAttemptResponse.jsonPath()
                .getString("error_detail.code");

        Allure.step("Verify that transaction failedn with statis code 400 and message code 'TXN_INSUFFICIENT_FUNDS'",
                () -> {
                    assertThat(statusCode).isEqualTo(400);
                    assertThat(messageCode).isEqualTo("TXN_INSUFFICIENT_FUNDS");
                });

        // we Take SQl Query Db Snapshots after transfer attempt
        BalanceRecord sourceAfterTransfer = fetchPersistedBalance(sourceBalance.balanceId());

        BalanceRecord destinationAfterTransfer = fetchPersistedBalance(destinationBalance.balanceId());

        // Then
        Allure.step("Verify that INV-0O2 holds for source balance", () -> {
            LedgerInvariantAssertions.assertSettledBalanceUnchanged(sourceBeforeTransfer,
                    sourceAfterTransfer);
        });

        Allure.step("Verify that INV-0O2 holds for destination balance", () -> {
            LedgerInvariantAssertions.assertSettledBalanceUnchanged(destinationBeforeTransfer,
                    destinationAfterTransfer);
        });

        Allure.step("Verify that INV-0O1 holds for source balance after transfer attempt", () -> {
            LedgerInvariantAssertions.assertDerivedBalanceInvariant(sourceAfterTransfer);
        });

        Allure.step("Verify that INV-0O1 holds for destination balance after transfer attempt", () -> {
            LedgerInvariantAssertions.assertDerivedBalanceInvariant(destinationAfterTransfer);
        });

    }

    @Test
    @Tag("FUN-07")
    @Story("FUN-07 - Transfer exceeding source balance with overdraft enabled")
    @DisplayName("should allow transfer exceeding source balance when overdraft is enabled")
    void shouldAllowTransferExceedingSourceBalanceWhenOverdraftIsEnabled() {

        long fundingAmount = 5_000;
        long transferAmount = 7_000;
        String currency = "NGN";
        int precision = 100;

        // Given
        CreateLedgerResponse ledger = createLedger(TestData.unique("fun-07-ledger"));

        CreateBalanceResponse worldBalance = createBalance(ledger.ledgerId(), currency);

        CreateBalanceResponse sourceBalance = createBalance(ledger.ledgerId(), currency);

        CreateBalanceResponse destinationBalance = createBalance(ledger.ledgerId(), currency);

        fundBalanceFromWorld(
                worldBalance.balanceId(),
                sourceBalance.balanceId(),
                fundingAmount,
                currency,
                precision);

        // Database snapshots before transfer
        BalanceRecord sourceBeforeTransfer = fetchPersistedBalance(sourceBalance.balanceId());

        BalanceRecord destinationBeforeTransfer = fetchPersistedBalance(destinationBalance.balanceId());

        // Verify preconditions
        Allure.step("Verify source and destination balances before transfer", () -> {

            // Source was funded with exactly 5,000
            assertThat(sourceBeforeTransfer.creditBalance())
                    .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

            assertThat(sourceBeforeTransfer.debitBalance())
                    .isEqualByComparingTo(BigDecimal.ZERO);

            assertThat(sourceBeforeTransfer.balance())
                    .isEqualByComparingTo(BigDecimal.valueOf(fundingAmount));

            // Destination starts empty
            assertThat(destinationBeforeTransfer.creditBalance())
                    .isEqualByComparingTo(BigDecimal.ZERO);

            assertThat(destinationBeforeTransfer.debitBalance())
                    .isEqualByComparingTo(BigDecimal.ZERO);

            assertThat(destinationBeforeTransfer.balance())
                    .isEqualByComparingTo(BigDecimal.ZERO);

            // Ensure this really is an overdraft scenario
            assertThat(BigDecimal.valueOf(transferAmount))
                    .isGreaterThan(sourceBeforeTransfer.balance());
        });

        // When
        CreateTransactionResponse transferResponse = makeTransfer(
                sourceBalance.balanceId(),
                destinationBalance.balanceId(),
                transferAmount,
                currency,
                precision,
                true, // allow overdraft
                true,
                false);

        // Database snapshots after transfer
        BalanceRecord sourceAfterTransfer = fetchPersistedBalance(sourceBalance.balanceId());

        BalanceRecord destinationAfterTransfer = fetchPersistedBalance(destinationBalance.balanceId());

        // Then
        Allure.step("Verify overdraft transfer was applied", () -> {

            assertThat(transferResponse.status())
                    .isEqualTo("APPLIED");

            assertThat(transferResponse.allowOverdraft())
                    .isTrue();

            assertThat(transferResponse.preciseAmount())
                    .isEqualTo(transferAmount);
        });

        Allure.step("Verify exact source balance effects", () -> {

            // Credit remains unchanged
            assertThat(sourceAfterTransfer.creditBalance())
                    .isEqualByComparingTo(
                            sourceBeforeTransfer.creditBalance());

            // Debit increases by transfer amount
            assertThat(sourceAfterTransfer.debitBalance())
                    .isEqualByComparingTo(
                            sourceBeforeTransfer.debitBalance()
                                    .add(BigDecimal.valueOf(transferAmount)));

            // Balance decreases by transfer amount
            assertThat(sourceAfterTransfer.balance())
                    .isEqualByComparingTo(
                            sourceBeforeTransfer.balance()
                                    .subtract(BigDecimal.valueOf(transferAmount)));

            // 5,000 - 7,000 = -2,000
            assertThat(sourceAfterTransfer.balance())
                    .isEqualByComparingTo(BigDecimal.valueOf(-2_000));
        });

        Allure.step("Verify exact destination balance effects", () -> {

            // Debit remains unchanged
            assertThat(destinationAfterTransfer.debitBalance())
                    .isEqualByComparingTo(
                            destinationBeforeTransfer.debitBalance());

            // Credit increases by transfer amount
            assertThat(destinationAfterTransfer.creditBalance())
                    .isEqualByComparingTo(
                            destinationBeforeTransfer.creditBalance()
                                    .add(BigDecimal.valueOf(transferAmount)));

            // Balance increases by transfer amount
            assertThat(destinationAfterTransfer.balance())
                    .isEqualByComparingTo(
                            destinationBeforeTransfer.balance()
                                    .add(BigDecimal.valueOf(transferAmount)));

            // 0 + 7,000 = 7,000
            assertThat(destinationAfterTransfer.balance())
                    .isEqualByComparingTo(BigDecimal.valueOf(7_000));
        });

        Allure.step("Verify INV-001 holds for source balance", () -> {
            LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                    sourceAfterTransfer);
        });

        Allure.step("Verify INV-001 holds for destination balance", () -> {
            LedgerInvariantAssertions.assertDerivedBalanceInvariant(
                    destinationAfterTransfer);
        });
    }
}
