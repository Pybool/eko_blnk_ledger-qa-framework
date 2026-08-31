package io.portfolio.ledgerqa.api;

import io.portfolio.ledgerqa.api.spec.RequestSpecFactory;
import io.restassured.specification.RequestSpecification;

public final class ApiClientFactory {

    private ApiClientFactory() {
    }

    public static TransactionClient transactionClient() {

        RequestSpecification spec = RequestSpecFactory.defaultSpec();

        return new TransactionClient(spec);
    }

    public static BalanceClient balanceClient() {

        RequestSpecification spec = RequestSpecFactory.defaultSpec();

        return new BalanceClient(spec);
    }

    public static LedgerClient ledgerClient() {

        RequestSpecification spec = RequestSpecFactory.defaultSpec();

        return new LedgerClient(spec);
    }
}