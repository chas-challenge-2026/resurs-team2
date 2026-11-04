package se.comerit.resurs.audit;

import com.fasterxml.jackson.annotation.JsonProperty;

import se.comerit.resurs.entity.ApplicationStatus;

public record StatusChanged(
        @JsonProperty("oldStatus") ApplicationStatus oldStatus,
        @JsonProperty("newStatus") ApplicationStatus newStatus) implements AuditEntry {

}
