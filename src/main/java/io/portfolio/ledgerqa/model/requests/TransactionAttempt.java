package io.portfolio.ledgerqa.model.requests;

import io.restassured.response.Response;

public record TransactionAttempt(
        String reference,
        Response response) {
}