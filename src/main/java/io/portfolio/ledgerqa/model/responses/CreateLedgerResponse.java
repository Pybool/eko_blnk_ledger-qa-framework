package io.portfolio.ledgerqa.model.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateLedgerResponse(
        @JsonProperty("ledger_id")
        String ledgerId,

        String name,

        @JsonProperty("created_at")
        String createdAt,

        @JsonProperty("meta_data")
        Metadata metadata
) {

    public record Metadata(
            @JsonProperty("project_owner")
            String projectOwner
    ) {}
}