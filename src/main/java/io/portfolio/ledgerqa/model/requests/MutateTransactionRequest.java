package io.portfolio.ledgerqa.model.requests;

public record MutateTransactionRequest(
    String transactionId,
    String status, 
    boolean skip_queue) 
{}
