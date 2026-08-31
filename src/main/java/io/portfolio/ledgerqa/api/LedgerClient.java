package io.portfolio.ledgerqa.api;

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

    public Response getById(String ledgerId) {
        return get(LEDGERS + "/" + ledgerId);
    }
}