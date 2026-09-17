package io.portfolio.ledgerqa.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.portfolio.ledgerqa.api.ApiClientFactory;
import io.portfolio.ledgerqa.model.requests.CreateBalanceRequest;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.functional.FunctionalTestBase;

import io.portfolio.ledgerqa.testsupport.TestData;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;


@Epic("Balance Quality Platform")
@Feature("Balance API Contract")
@Tag("contract")
class BalanceCreationValidationTests extends FunctionalTestBase {

    @Test
    @Story("Balance API Validation")
    @DisplayName("should not create a balance when the ledger does not exist")
    @Severity(SeverityLevel.CRITICAL)
    void shouldRejectBalanceCreationWhenLedgerDoesNotExist() {
        String missingLedgerId = TestData.unique("ldg_missing");

        // When
        Response response = Allure.step(
                "Attempt to create a balance under a non-existent ledger",
                () -> ApiClientFactory.balanceClient()
                        .create(new CreateBalanceRequest(missingLedgerId, "NGN")));
        // Then
        validateBalanceNotCreated(response);
    }

    @Test
    @Story("Balance API Validation")
    @DisplayName("should not create a balance when 'ledger_id' is missing")
    @Severity(SeverityLevel.NORMAL)
    void shouldRejectBalanceCreationWhenLedgerIdIsMissing() {
        // When
        Response response = Allure.step(
                "Attempt to create a balance without ledger_id",
                () -> ApiClientFactory.balanceClient().createRaw("""
                        { "currency": "NGN" }
                        """));
        // Then
        validateBalanceNotCreated(response);
    }

    @Test
    @Story("Balance API Validation")
    @DisplayName("should not create a balance when 'currency' is missing")
    @Severity(SeverityLevel.NORMAL)
    void shouldRejectBalanceCreationWhenCurrencyIsMissing() {
        // Given
        CreateLedgerResponse ledger = createLedger(TestData.unique("balance-persistence"));

        // When
        Response response = Allure.step(
                "Attempt to create a balance without currency",
                () -> ApiClientFactory.balanceClient().createRaw("""
                        { "ledger_id": "%s" }
                        """.formatted(ledger.ledgerId())));

        // Then
        validateBalanceNotCreated(response);
    }

    @Test
    @Story("Balance API Validation")
    @DisplayName("should not create a balance with an unknown currency code")
    @Severity(SeverityLevel.MINOR)
    void shouldRejectBalanceCreationWhenCurrencyIsUnknownCode() {
        //Given
        CreateLedgerResponse ledger = createLedger(TestData.unique("balance-persistence"));

        //When
        Response response = Allure.step(
                "Attempt to create a balance with a non-ISO currency code",
                () -> ApiClientFactory.balanceClient()
                        .create(new CreateBalanceRequest(ledger.ledgerId(), "ZZZ")));
        //Then
        validateBalanceNotCreated(response);
    }

    @Test
    @Story("Balance API Validation")
    @DisplayName("should not create a balance when the payload is empty")
    @Severity(SeverityLevel.NORMAL)
    void shouldRejectBalanceCreationWhenPayloadIsEmpty() {
        //When
        Response response = Allure.step(
                "Attempt to create a balance with an empty payload",
                () -> ApiClientFactory.balanceClient().createRaw("{}"));
        //Then
        validateBalanceNotCreated(response);
    }

    @Test
    @Story("Balance API Validation")
    @DisplayName("should not create a balance when the payload is malformed JSON")
    @Severity(SeverityLevel.NORMAL)
    void shouldRejectBalanceCreationWhenPayloadIsMalformedJson() {
        //When
        Response response = Allure.step(
                "Attempt to create a balance with malformed JSON",
                () -> ApiClientFactory.balanceClient().createRaw("""
                        { "ledger_id": "ldg_x", "currency":
                        """));
        //Then
        validateBalanceNotCreated(response);
    }

    private void validateBalanceNotCreated(Response response) {
        attachJson("API Rejection Response HTTP " + response.statusCode(), response);
        Allure.step("Verify balance creation was rejected", () -> {
            assertThat(response.statusCode()).isBetween(400, 499);
            assertThat(response.getBody().asString()).isNotBlank();
        });
    }
}
