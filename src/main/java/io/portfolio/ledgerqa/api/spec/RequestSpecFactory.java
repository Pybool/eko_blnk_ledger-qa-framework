package io.portfolio.ledgerqa.api.spec;

import io.portfolio.ledgerqa.config.TestConfig;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public final class RequestSpecFactory {

    private RequestSpecFactory() {
    }

    public static RequestSpecification defaultSpec() {

        return new RequestSpecBuilder()
                .setBaseUri(TestConfig.baseUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();
    }
}