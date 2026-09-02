package io.portfolio.ledgerqa.db.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LedgerRecord(
        String name,
        String ledgerId,
        Metadata metadata,
        Instant createdAt) {
    public record Metadata(
            @JsonProperty("project_owner") 
            String projectOwner) {
    }
}
