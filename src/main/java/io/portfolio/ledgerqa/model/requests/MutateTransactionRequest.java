package io.portfolio.ledgerqa.model.requests;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MutateTransactionRequest(
    String transactionId,
    String status, 
    boolean skip_queue,
    Long precise_amount) 
{}
