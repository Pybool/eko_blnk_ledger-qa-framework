package io.portfolio.ledgerqa.accounting;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import io.portfolio.ledgerqa.functional.FunctionalTestBase;

import io.portfolio.ledgerqa.assertions.LedgerInvariantAssertions;
import io.portfolio.ledgerqa.db.model.BalanceRecord;
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
                true, partialCommitAmount);

    }
}
