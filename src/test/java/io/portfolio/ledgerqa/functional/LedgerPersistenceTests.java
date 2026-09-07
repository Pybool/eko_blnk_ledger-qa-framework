package io.portfolio.ledgerqa.functional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.portfolio.ledgerqa.api.ApiClientFactory;
import io.portfolio.ledgerqa.api.LedgerClient;
import io.portfolio.ledgerqa.base.BaseTest;
import io.portfolio.ledgerqa.db.RepositoryFactory;
import io.portfolio.ledgerqa.db.model.LedgerRecord;
import io.portfolio.ledgerqa.db.repository.LedgerRepository;
import io.portfolio.ledgerqa.helpers.Helpers;
import io.portfolio.ledgerqa.model.requests.CreateLedgerRequest;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.model.responses.FetchedLedgerResponse;
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
@Feature("Ledgers")
@Tag("integration")
class LedgerPersistenceTests extends BaseTest {

    @Test
    @Story("Ledger Persistence (Data Integrity)")
    @DisplayName("should persist created ledger correctly")
    @Severity(SeverityLevel.CRITICAL)
    void shouldPersistCreatedLedgerCorrectly() {
        String ledgerName = TestData.unique("qa-ledger");
        String projectOwner = TestData.unique("EKO-SDET");

        LedgerClient ledgerClient = ApiClientFactory.ledgerClient();

        CreateLedgerRequest request = CreateLedgerRequest.of(
                ledgerName,
                projectOwner);

        Response ledgerResponse = Allure.step(
                "Create ledger via POST /ledgers",
                () -> ledgerClient.create(request));

        CreateLedgerResponse createdLedger = Allure.step(
                "Deserialize created ledger response",
                () -> ledgerResponse.as(CreateLedgerResponse.class));

        Allure.addAttachment(
                "Create Ledger API Response",
                "application/json",
                ledgerResponse.getBody().asPrettyString());

        Allure.step("Verify API returns valid 201 response", () -> {

            assertThat(ledgerResponse.statusCode()).isEqualTo(201);
            assertThat(createdLedger.ledgerId()).startsWith("ldg_");
            assertThat(createdLedger.name()).isEqualTo(ledgerName);
            assertThat(createdLedger.metadata().projectOwner()).isEqualTo(projectOwner);
        });

        LedgerRepository ledgerRepository = RepositoryFactory.ledgerRepository();
        LedgerRecord persistedLedger = ledgerRepository
                .findById(createdLedger.ledgerId())
                .orElseThrow(() -> new AssertionError(
                        "Ledger was not persisted in database: " + createdLedger.ledgerId()));

        Allure.addAttachment(
                "Database Query Response",
                "application/json",
                Helpers.toPrettyJson(persistedLedger));

        Allure.step("Data Integrity Test to verify ledger data returned via API matches ledger data in database",
                () -> {

                    assertThat(persistedLedger.ledgerId())
                            .isEqualTo(createdLedger.ledgerId());

                    assertThat(persistedLedger.name()).isEqualTo(createdLedger.name());
                    assertThat(persistedLedger.metadata()).isNotNull();

                    assertThat(persistedLedger.metadata().projectOwner())
                            .isEqualTo(createdLedger.metadata().projectOwner());
                });

    }

    @Test
    @Story("Ledger API Consistency")
    @DisplayName("should fetch a ledger with it's correct details")
    @Severity(SeverityLevel.NORMAL)
    void shouldBeAbleToFetchAndViewLedgerDetails() {
        String ledgerName = TestData.unique("qa-ledger");
        String projectOwner = TestData.unique("EKO-SDET");

        LedgerClient ledgerClient = ApiClientFactory.ledgerClient();

        CreateLedgerRequest request = CreateLedgerRequest.of(
                ledgerName,
                projectOwner);

        Response ledgerResponse = Allure.step(
                "Create ledger via POST /ledgers",
                () -> ledgerClient.create(request));

        CreateLedgerResponse createdLedger = Allure.step(
                "Deserialize created ledger response",
                () -> ledgerResponse.as(CreateLedgerResponse.class));

        Allure.addAttachment(
                "API Creation Response",
                "application/json",
                Helpers.toPrettyJson(createdLedger));

        assertThat(ledgerResponse.statusCode()).isEqualTo(201);
        assertThat(createdLedger.ledgerId()).startsWith("ldg_");

        Response ledgerDetailsResponse = Allure.step(
                "Fetch ledger details via GET /ledgers",
                () -> ledgerClient.getById(createdLedger.ledgerId()));

        Allure.addAttachment(
                "API Ledger Fetch Response",
                "application/json",
                ledgerDetailsResponse.getBody().asPrettyString());

        assertThat(ledgerDetailsResponse.statusCode()).isEqualTo(200);

        FetchedLedgerResponse fetchedLedger = Allure.step(
                "Deserialize fetched ledger details response",
                () -> ledgerDetailsResponse.as(FetchedLedgerResponse.class));

        Allure.step("Data Integrity Test to verify ledger data created via API matches ledger data in API response",
                () -> {

                    assertThat(createdLedger.ledgerId()).isEqualTo(fetchedLedger.ledgerId());
                    assertThat(createdLedger.name()).isEqualTo(fetchedLedger.name());
                    assertThat(createdLedger.metadata()).isNotNull();
                    assertThat(createdLedger.metadata().projectOwner())
                            .isEqualTo(fetchedLedger.metadata().projectOwner());
                });

    }

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
