package io.portfolio.ledgerqa.model.requests;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateBalanceRequest(

        @JsonProperty("ledger_id") String ledgerId,

        String currency,

        @JsonProperty("identity_id") String identityId) {
}