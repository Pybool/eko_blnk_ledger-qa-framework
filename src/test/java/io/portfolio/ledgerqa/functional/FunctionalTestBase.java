package io.portfolio.ledgerqa.functional;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import io.portfolio.ledgerqa.api.ApiClientFactory;
import io.portfolio.ledgerqa.base.BaseTest;
import io.portfolio.ledgerqa.db.RepositoryFactory;
import io.portfolio.ledgerqa.db.model.BalanceRecord;
import io.portfolio.ledgerqa.db.model.TransactionRecord;
import io.portfolio.ledgerqa.db.repository.TransactionRepository;
import io.portfolio.ledgerqa.model.requests.CreateBalanceRequest;
import io.portfolio.ledgerqa.model.requests.CreateLedgerRequest;
import io.portfolio.ledgerqa.model.requests.CreateTransactionRequest;
import io.portfolio.ledgerqa.model.requests.FetchTransactionRequest;
import io.portfolio.ledgerqa.model.responses.CreateBalanceResponse;
import io.portfolio.ledgerqa.model.responses.CreateLedgerResponse;
import io.portfolio.ledgerqa.model.responses.CreateTransactionResponse;
import io.portfolio.ledgerqa.model.responses.FetchTransactionResponse;
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
                                                .create(new CreateBalanceRequest(ledgerId, currency)));

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

        protected CreateTransactionResponse fundBalanceFromWorld(
                        String worldBalanceId,
                        String destinationBalanceId,
                        long amount,
                        String currency,
                        int precision) {
                String reference = TestData.unique("world-fund-ref");

                CreateTransactionRequest request = new CreateTransactionRequest(
                                "Fund balance from world",
                                reference,
                                worldBalanceId,
                                destinationBalanceId,
                                amount,
                                currency,
                                precision,
                                true,
                                true,
                                false);

                Response response = Allure.step(
                                "Fund %s balance with %d from world".formatted(
                                                currency,
                                                amount),
                                () -> ApiClientFactory.transactionClient()
                                                .create(request));

                attachJson("World Funding Transaction Response", response);

                assertThat(response.statusCode())
                                .as("World funding transaction should succeed")
                                .isBetween(200, 299);

                return response.as(CreateTransactionResponse.class);
        }

        protected CreateTransactionResponse makeTransfer(
                        String sourceBalanceId,
                        String destinationBalanceId,
                        long amount,
                        String currency,
                        int precision,
                        Boolean allowOverdraft,
                        Boolean skipQueue,
                        Boolean inflight) {
                String reference = TestData.unique("make-transfer-ref");

                CreateTransactionRequest request = new CreateTransactionRequest(
                                "Transfer from source balance to destination balance",
                                reference,
                                sourceBalanceId,
                                destinationBalanceId,
                                amount,
                                currency,
                                precision,
                                allowOverdraft,
                                skipQueue,
                                inflight);

                Response response = Allure.step(
                                "Fund %s balance with %d from a source".formatted(
                                                currency,
                                                amount),
                                () -> ApiClientFactory.transactionClient()
                                                .create(request));

                attachJson("Transfer Transaction Response", response);

                assertThat(response.statusCode())
                                .as("Transfer transaction should succeed")
                                .isBetween(200, 299);

                return response.as(CreateTransactionResponse.class);
        }

        protected Response makeTransferToFail(
                        String sourceBalanceId,
                        String destinationBalanceId,
                        long amount,
                        String currency,
                        int precision,
                        boolean allowOverdraft,
                        boolean skipQueue,
                        boolean inflight) {

                String reference = TestData.unique("failed-transfer-ref");

                CreateTransactionRequest request = new CreateTransactionRequest(
                                "Transfer expected to fail",
                                reference,
                                sourceBalanceId,
                                destinationBalanceId,
                                amount,
                                currency,
                                precision,
                                allowOverdraft,
                                skipQueue,
                                inflight);

                Response response = Allure.step(
                                "Attempt transfer of %d %s expected to fail"
                                                .formatted(amount, currency),
                                () -> ApiClientFactory.transactionClient()
                                                .create(request));

                attachJson("Failed Transfer Transaction Response", response);

                assertThat(response.statusCode())
                                .as("Transfer transaction should be rejected")
                                .isBetween(400, 499);

                return response;
        }

        protected BalanceRecord fetchPersistedBalance(
                        String balanceId) {
                return RepositoryFactory.balanceRepository()
                                .findById(balanceId)
                                .orElseThrow(() -> new AssertionError(
                                                "Balance was not persisted in database: " + balanceId));
        }

        protected TransactionRecord fetchPersistedTransaction(
                        String transactionId) {
                return RepositoryFactory.transactionRepository()
                                .findById(transactionId)
                                .orElseThrow(() -> new AssertionError(
                                                "Transaction was not persisted in database: " + transactionId));
        }

        protected FetchTransactionResponse fetchTransactionViaApi(
                        String transactionId) {

                Response response = Allure.step(
                                "Fetch transaction details for %s via api".formatted(
                                                transactionId),
                                () -> ApiClientFactory.transactionClient()
                                                .getById(transactionId));
                attachJson("Failed Transfer Transaction Response", response);

                assertThat(response.statusCode())
                                .as("Transfer transaction should be retrieved")
                                .isEqualTo(200);

                return response.as(FetchTransactionResponse.class);

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

        protected record FundedBalanceFixture(
                        CreateLedgerResponse ledger,
                        CreateBalanceResponse world,
                        CreateBalanceResponse balance,
                        CreateTransactionResponse fundingTransaction) {
        }

        protected FundedBalanceFixture createFundedBalance(
                        long amount,
                        String currency,
                        int precision) {
                CreateLedgerResponse ledger = createLedger(TestData.unique("funded-balance"));

                CreateBalanceResponse world = createBalance(ledger.ledgerId(), currency);

                CreateBalanceResponse balance = createBalance(ledger.ledgerId(), currency);

                CreateTransactionResponse fundingTransaction = fundBalanceFromWorld(
                                world.balanceId(),
                                balance.balanceId(),
                                amount,
                                currency,
                                precision);

                return new FundedBalanceFixture(
                                ledger,
                                world,
                                balance,
                                fundingTransaction);
        }

        protected record CreatedLedgerAndBalance(
                        CreateLedgerResponse ledger,
                        CreateBalanceResponse balance,
                        Response response) {
        }

}