package io.portfolio.ledgerqa.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

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
import io.restassured.response.Response;

public abstract class FunctionalTestBase extends BaseTest {

        protected CreateLedgerResponse createLedger(String projectOwner) {
                CreateLedgerRequest request = CreateLedgerRequest.of(
                                TestData.unique("ledger"),
                                projectOwner);

                Response response = Allure.step(
                                "Create ledger",
                                () -> ApiClientFactory.ledgerClient().create(request));

                attachJson("Create Ledger Response", response);

                assertThat(response.statusCode())
                                .as("Ledger creation should succeed")
                                .isEqualTo(201);

                return response.as(CreateLedgerResponse.class);
        }

        protected CreateBalanceResponse createBalance(
                        String ledgerId,
                        String currency) {
                Response response = postBalance(ledgerId, currency);

                assertThat(response.statusCode())
                                .as("Balance creation should succeed")
                                .isEqualTo(201);

                return response.as(CreateBalanceResponse.class);
        }

        protected Response postBalance(
                        String ledgerId,
                        String currency) {
                Response response = Allure.step(
                                "Create %s balance".formatted(currency),
                                () -> ApiClientFactory.balanceClient()
                                                .create(
                                                                new CreateBalanceRequest(
                                                                                ledgerId,
                                                                                currency)));

                attachJson("Create Balance Response", response);

                return response;
        }

        protected CreatedLedgerAndBalance createLedgerAndBalance() {
                return createLedgerAndBalance("NGN");
        }

        protected CreatedLedgerAndBalance createLedgerAndBalance(
                        String currency) {
                CreateLedgerResponse ledger = createLedger(TestData.unique("functional-test"));

                Response response = postBalance(ledger.ledgerId(), currency);

                assertThat(response.statusCode())
                                .as("Balance creation should succeed")
                                .isEqualTo(201);

                CreateBalanceResponse balance = response.as(CreateBalanceResponse.class);

                return new CreatedLedgerAndBalance(
                                ledger,
                                balance,
                                response);
        }

        protected BalanceRecord fetchPersistedBalance(
                        String balanceId) {
                return RepositoryFactory.balanceRepository()
                                .findById(balanceId)
                                .orElseThrow(
                                                () -> new AssertionError(
                                                                "Balance was not persisted in database: "
                                                                                + balanceId));
        }

        protected void assertDerivedBalanceInvariant(
                        BalanceRecord balance) {
                BigDecimal expected = balance.creditBalance()
                                .subtract(balance.debitBalance());

                assertThat(balance.balance())
                                .as(
                                                "INV-001 derived balance for %s",
                                                balance.balanceId())
                                .isEqualByComparingTo(expected);
        }

        protected void attachJson(
                        String name,
                        Response response) {
                String body = response.getBody().asPrettyString();

                System.out.printf(
                                "%n===== %s =====%n%s%n%s%n=====================%n",
                                name,
                                response.getStatusLine(),
                                body);

                Allure.addAttachment(
                                name,
                                "application/json",
                                body);
        }

        protected record CreatedLedgerAndBalance(
                        CreateLedgerResponse ledger,
                        CreateBalanceResponse balance,
                        Response response) {
        }

}