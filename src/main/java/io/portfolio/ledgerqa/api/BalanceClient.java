package io.portfolio.ledgerqa.api;

import io.portfolio.ledgerqa.model.requests.CreateBalanceRequest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public final class BalanceClient extends BaseClient {

    private static final String BALANCES = "/balances";

    public BalanceClient(RequestSpecification requestSpec) {
        super(requestSpec);
    }

    public Response create(CreateBalanceRequest request) {
        return post(BALANCES, request);
    }

    public Response getById(String balanceId) {
        return get(BALANCES + "/" + balanceId);
    }
}