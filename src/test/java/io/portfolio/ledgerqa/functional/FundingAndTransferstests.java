package io.portfolio.ledgerqa.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.portfolio.ledgerqa.api.ApiClientFactory;
import io.portfolio.ledgerqa.base.BaseTest;
import io.portfolio.ledgerqa.db.RepositoryFactory;
import io.portfolio.ledgerqa.db.model.BalanceRecord;
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
@Tag("functional")
class FundingAndTransferTests extends BaseTest {

    @Test
    @Story("FUN-01 - Fund a balance")
    @DisplayName("should increase the destination balance by the exact funded amount")
    void shouldIncreaseDestinationBalanceByExactFundedAmount() {

        // Given
        // Create ledger
        // Create balance

        // When
        // Fund balance from @world using skip_queue=true

        // Then
        // Transaction is APPLIED
        // credit_balance increased by exact precise_amount
        // balance increased by exact precise_amount
        // debit_balance remains unchanged
        // API state agrees with DB state
    }
}