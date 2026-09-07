package io.portfolio.ledgerqa.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.portfolio.ledgerqa.api.ApiClientFactory;
import io.portfolio.ledgerqa.api.BalanceClient;
import io.portfolio.ledgerqa.api.LedgerClient;
import io.portfolio.ledgerqa.base.BaseTest;
import io.portfolio.ledgerqa.model.requests.CreateBalanceRequest;
import io.portfolio.ledgerqa.model.requests.CreateLedgerRequest;
import io.portfolio.ledgerqa.model.responses.CreateBalanceResponse;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
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
@Feature("Balances")
@Tag("smoke")
class BalanceSmokeTests extends BaseTest {

    @Test
    @Story("Balance Creation")
    @DisplayName("should create a balance successfully")
    @Description("""
            Verifies that an NGN balance can be created successfully
            under an existing ledger.
            """)
    @Severity(SeverityLevel.CRITICAL)
    void createBalance() {

        LedgerClient ledgerClient = ApiClientFactory.ledgerClient();

        BalanceClient balanceClient = ApiClientFactory.balanceClient();

        String ledgerName = TestData.unique("qa-ledger");

        String currency = "NGN";

        CreateLedgerRequest ledgerRequest = CreateLedgerRequest.of(
                ledgerName,
                "EKO-SDET");

        Response ledgerResponse = Allure.step(
                "Create parent ledger",
                () -> ledgerClient.create(ledgerRequest));

        assertThat(ledgerResponse.statusCode())
                .isEqualTo(201);

        CreateLedgerResponse createdLedger = ledgerResponse.as(CreateLedgerResponse.class);

        String ledgerId = createdLedger.ledgerId();

        CreateBalanceRequest balanceRequest = new CreateBalanceRequest(
                ledgerId,
                currency);

        Response balanceResponse = Allure.step(
                "Create NGN balance under ledger " + ledgerId,
                () -> balanceClient.create(balanceRequest));

        CreateBalanceResponse createdBalance = balanceResponse.as(CreateBalanceResponse.class);

        Allure.step("Verify created balance response contract", () -> {

            assertThat(balanceResponse.statusCode())
                    .isEqualTo(201);

            assertThat(createdBalance.balanceId())
                    .startsWith("bln_");

            assertThat(createdBalance.ledgerId())
                    .isEqualTo(ledgerId);

            assertThat(createdBalance.currency())
                    .isEqualTo(currency);

            assertThat(createdBalance.balance())
                    .isZero();

            assertThat(createdBalance.creditBalance())
                    .isZero();

            assertThat(createdBalance.debitBalance())
                    .isZero();

            assertThat(createdBalance.inflightBalance())
                    .isZero();

            assertThat(createdBalance.inflightCreditBalance())
                    .isZero();

            assertThat(createdBalance.inflightDebitBalance())
                    .isZero();

            assertThat(createdBalance.version())
                    .isZero();

            assertThat(createdBalance.createdAt())
                    .isNotBlank();

            assertThat(createdBalance.trackFundLineage())
                    .isFalse();

            assertThat(createdBalance.allocationStrategy())
                    .isEqualTo("FIFO");
        });
    }
}