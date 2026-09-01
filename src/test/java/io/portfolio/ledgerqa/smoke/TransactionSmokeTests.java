package io.portfolio.ledgerqa.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.portfolio.ledgerqa.api.ApiClientFactory;
import io.portfolio.ledgerqa.api.BalanceClient;
import io.portfolio.ledgerqa.api.LedgerClient;
import io.portfolio.ledgerqa.api.TransactionClient;
import io.portfolio.ledgerqa.base.BaseTest;
import io.portfolio.ledgerqa.model.requests.CreateBalanceRequest;
import io.portfolio.ledgerqa.model.requests.CreateLedgerRequest;
import io.portfolio.ledgerqa.model.requests.CreateTransactionRequest;
import io.portfolio.ledgerqa.model.responses.CreateBalanceResponse;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.model.responses.CreateTransactionResponse;
import io.portfolio.ledgerqa.testsupport.TestData;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;

@Epic("Ledger Quality Platform")
@Feature("Transactions")
@Tag("smoke")
class TransactionSmokeTests extends BaseTest {

    @Test
    @Story("Transaction Creation")
    @DisplayName("should create a transaction successfully")
    @Description("""
            Verifies that funds can be moved successfully
            between two balances in the same ledger.
            """)
    @Severity(SeverityLevel.CRITICAL)
    void createTransaction() {

        LedgerClient ledgerClient = ApiClientFactory.ledgerClient();

        BalanceClient balanceClient = ApiClientFactory.balanceClient();

        TransactionClient transactionClient = ApiClientFactory.transactionClient();

        String ledgerName = TestData.unique("qa-ledger");

        String fundingReference = TestData.unique("qa-fund-ref");

        String transferReference = TestData.unique("qa-trx-ref");

        String currency = "NGN";

        int precision = 100;

        long fundingAmount = 10_000;
        long transferAmount = 5_000;

        CreateLedgerRequest ledgerRequest = CreateLedgerRequest.of(
                ledgerName,
                "Blnk Tech");

        Response ledgerResponse = Allure.step(
                "Create parent ledger",
                () -> ledgerClient.create(ledgerRequest));

        assertThat(ledgerResponse.statusCode())
                .isEqualTo(201);

        CreateLedgerResponse createdLedger = ledgerResponse.as(CreateLedgerResponse.class);

        String ledgerId = createdLedger.ledgerId();

        CreateBalanceRequest worldBalanceRequest = new CreateBalanceRequest(
                ledgerId,
                currency);

        Response worldBalanceResponse = Allure.step(
                "Create world funder NGN balance",
                () -> balanceClient.create(worldBalanceRequest));

        assertThat(worldBalanceResponse.statusCode())
                .isEqualTo(201);

        CreateBalanceResponse worldBalance = worldBalanceResponse.as(CreateBalanceResponse.class);

        CreateBalanceRequest senderBalanceRequest = new CreateBalanceRequest(
                ledgerId,
                currency);

        Response senderBalanceResponse = Allure.step(
                "Create sender NGN balance",
                () -> balanceClient.create(senderBalanceRequest));

        assertThat(senderBalanceResponse.statusCode())
                .isEqualTo(201);

        CreateBalanceResponse senderBalance = senderBalanceResponse.as(CreateBalanceResponse.class);

        CreateBalanceRequest recipientBalanceRequest = new CreateBalanceRequest(
                ledgerId,
                currency);

        Response recipientBalanceResponse = Allure.step(
                "Create recipient NGN balance",
                () -> balanceClient.create(recipientBalanceRequest));

        assertThat(recipientBalanceResponse.statusCode())
                .isEqualTo(201);

        CreateBalanceResponse recipientBalance = recipientBalanceResponse.as(CreateBalanceResponse.class);

        CreateTransactionRequest fundingRequest = new CreateTransactionRequest(
                "Fund sender balance",
                fundingReference,
                worldBalance.balanceId(),
                senderBalance.balanceId(),
                fundingAmount,
                currency,
                precision,
                true,
                true,
                false);

        Response fundingResponse = Allure.step(
                "Fund sender balance from world funder",
                () -> transactionClient.create(fundingRequest));

        assertThat(fundingResponse.statusCode())
                .isBetween(200, 299);

        CreateTransactionRequest transferRequest = new CreateTransactionRequest(
                "Transfer from sender to receipient",
                transferReference,
                senderBalance.balanceId(),
                recipientBalance.balanceId(),
                transferAmount,
                currency,
                precision,
                false,
                true,
                false);

        Response transactionResponse = Allure.step(
                "Transfer funds from sender to recipient",
                () -> transactionClient.create(transferRequest));

        CreateTransactionResponse createdTransaction = transactionResponse.as(CreateTransactionResponse.class);

        Allure.step(
                "Verify transaction was created successfully",
                () -> {
                    assertThat(transactionResponse.statusCode())
                            .isEqualTo(201);

                    assertThat(createdTransaction.transactionId())
                            .startsWith("txn_");

                    assertThat(createdTransaction.reference())
                            .isEqualTo(transferReference);

                    assertThat(createdTransaction.source())
                            .isEqualTo(senderBalance.balanceId());

                    assertThat(createdTransaction.destination())
                            .isEqualTo(recipientBalance.balanceId());

                    assertThat(createdTransaction.preciseAmount())
                            .isEqualTo(transferAmount);

                    assertThat(createdTransaction.amount())
                            .isEqualTo(50);

                    assertThat(createdTransaction.precision())
                            .isEqualTo(precision);

                    assertThat(createdTransaction.currency())
                            .isEqualTo(currency);

                    assertThat(createdTransaction.status())
                            .isEqualTo("APPLIED");

                    assertThat(createdTransaction.allowOverdraft())
                            .isFalse();

                    assertThat(createdTransaction.inflight())
                            .isFalse();

                    assertThat(createdTransaction.skipQueue())
                            .isTrue();

                    assertThat(createdTransaction.transactionId())
                            .isNotBlank();

                    assertThat(createdTransaction.hash())
                            .isNotBlank();

                    assertThat(createdTransaction.createdAt())
                            .isNotBlank();

                    assertThat(createdTransaction.effectiveDate())
                            .isNotBlank();
                });
    }
}