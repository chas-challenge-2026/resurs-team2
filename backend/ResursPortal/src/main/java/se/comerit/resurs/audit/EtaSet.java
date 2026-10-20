package se.comerit.resurs.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Records that an application's estimated resolution time was set, refreshed
 * or cleared. {@code estimatedResolutionAt} is the ISO-8601 UTC timestamp of
 * the ETA; it is omitted ({@code null}) when the ETA was cleared.
 */
public record EtaSet(
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("estimatedResolutionAt") String estimatedResolutionAt) implements AuditEntry {
}