package io.portfolio.ledgerqa.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.portfolio.ledgerqa.api.ApiClientFactory;
import io.portfolio.ledgerqa.api.LedgerClient;
import io.portfolio.ledgerqa.base.BaseTest;
import io.portfolio.ledgerqa.db.RepositoryFactory;
import io.portfolio.ledgerqa.db.repository.LedgerRepository;
import io.portfolio.ledgerqa.testsupport.TestData;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.restassured.response.Response;

@Epic("Ledger Quality Platform")
@Feature("Ledger API Contract")
@Tag("contract")
class LedgerCreationValidationTests extends BaseTest {
    @Test
    @Story("Ledger API test")
    @DisplayName("should not create a ledger when 'name' field is missing in payload")
    @Severity(SeverityLevel.NORMAL)
    void shouldNotPersistLedgerWhenNameIsMissing() {
        String projectOwner = TestData.unique("EKO-SDET");
        String payload = """
                {
                  "meta_data": {
                    "project_owner": "%s"
                  }
                }
                """.formatted(projectOwner);

        validateBadRequestsLedgerCreation(payload);
    }

    @Test
    @Story("Ledger API test")
    @DisplayName("should not create a ledger when 'name' field is empty string in payload")
    @Severity(SeverityLevel.NORMAL)
    void shouldNotPersistLedgerWhenNameIsBlank() {
        String projectOwner = TestData.unique("EKO-SDET");
        String payload = """
                {
                  "name": "",
                  "meta_data": {
                    "project_owner": "%s"
                  }
                }
                """.formatted(projectOwner);

        validateBadRequestsLedgerCreation(payload);
    }

    @Test
    @Story("Ledger API test")
    @DisplayName("should not create a ledger when 'name' field is null")
    @Severity(SeverityLevel.NORMAL)
    void shouldNotPersistLedgerWhenNameIsNull() {
        String projectOwner = TestData.unique("EKO-SDET");
        String payload = """
                {
                  "name": null,
                  "meta_data": {
                    "project_owner": "%s"
                  }
                }
                """.formatted(projectOwner);

        validateBadRequestsLedgerCreation(payload);
    }

    @Test
    @Story("Ledger API test")
    @DisplayName("should not create a ledger when payload is empty")
    @Severity(SeverityLevel.NORMAL)
    void shouldRejectLedgerCreationWhenPayloadIsEmpty() {
        String projectOwner = TestData.unique("EKO-SDET");
        String payload = """
                {}
                """.formatted(projectOwner);

        validateBadRequestsLedgerCreation(payload);
    }

    @Test
    @Story("Ledger API test")
    @DisplayName("should not create a ledger when payload is malformed")
    @Severity(SeverityLevel.NORMAL)
    void shouldRejectLedgerCreationWhenRequestBodyIsMalformedJson() {
        String payload = """
                {
                  "name": "qa-ledger",
                  "meta_data":
                """;
        validateBadRequestsLedgerCreation(payload);
    }

    private void validateBadRequestsLedgerCreation(String payload) {
        String projectOwner = TestData.unique("EKO-SDET");
        LedgerClient ledgerClient = ApiClientFactory.ledgerClient();
        Response response = ledgerClient.createRaw(payload);
        validateNotCreated(response, projectOwner);
    }

    private void validateNotCreated(
            Response response,
            String projectOwner) {
        Allure.addAttachment(
                "API Rejection Response HTTP " + response.statusCode(),
                "application/json",
                response.getBody().asPrettyString());

        Allure.step("Verify ledger creation was rejected", () -> {
            assertThat(response.statusCode()).isBetween(400, 499);
            assertThat(response.getBody().asString()).isNotBlank();
        });

        LedgerRepository ledgerRepository = RepositoryFactory.ledgerRepository();

        Allure.step("Verify rejected ledger was not persisted", () -> {
            assertThat(
                    ledgerRepository.findByProjectOwner(projectOwner)).isEmpty();
        });
    }
}