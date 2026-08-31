package io.portfolio.ledgerqa.api;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

public abstract class BaseClient {

    protected final RequestSpecification requestSpec;

    protected BaseClient(
            RequestSpecification requestSpec
    ) {
        this.requestSpec = requestSpec;
    }

    protected Response get(
            String path
    ) {
        return given()
                .spec(requestSpec)
                .when()
                .get(path);
    }

    protected Response post(
            String path,
            Object body
    ) {
        return given()
                .spec(requestSpec)
                .body(body)
                .when()
                .post(path);
    }

    protected Response put(
            String path,
            Object body
    ) {
        return given()
                .spec(requestSpec)
                .body(body)
                .when()
                .put(path);
    }

    protected Response delete(
            String path
    ) {
        return given()
                .spec(requestSpec)
                .when()
                .delete(path);
    }
}