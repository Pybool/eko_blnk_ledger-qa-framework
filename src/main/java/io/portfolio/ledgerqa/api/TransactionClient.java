package io.portfolio.ledgerqa.api;

import io.portfolio.ledgerqa.model.requests.CreateTransactionRequest;
import io.portfolio.ledgerqa.model.requests.UpdateInflightRequest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public final class TransactionClient extends BaseClient {

    private static final String TRANSACTIONS = "/transactions";

    public TransactionClient(RequestSpecification requestSpec) {
        super(requestSpec);
    }

    public Response create(CreateTransactionRequest request) {
        return post(TRANSACTIONS, request);
    }

    public Response getById(String transactionId) {
        return get(TRANSACTIONS + "/" + transactionId);
    }

    public Response updateInflight(String transactionId, UpdateInflightRequest request) {
        return put(TRANSACTIONS + "/inflight/" + transactionId, request);
    }
}