package io.portfolio.ledgerqa.api;

import io.portfolio.ledgerqa.model.requests.CreateLedgerRequest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public final class LedgerClient extends BaseClient {

    private static final String LEDGERS = "/ledgers";

    public LedgerClient(RequestSpecification requestSpec) {
        super(requestSpec);
    }

    public Response health() {
        return get("/");
    }

    public Response create(CreateLedgerRequest request) {
        return post(LEDGERS, request);
    }

    public Response getById(String ledgerId) {
        return get(LEDGERS + "/" + ledgerId);
    }
}