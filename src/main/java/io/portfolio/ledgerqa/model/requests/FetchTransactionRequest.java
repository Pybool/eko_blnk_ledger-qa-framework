package io.portfolio.ledgerqa.model.requests;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FetchTransactionRequest(@JsonProperty("transaction_id") String transactionId) {

}