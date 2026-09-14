package se.comerit.resurs.audit;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A tamper-evident audit entry attached to an {@code Application}. The sealed
 * type is serialized to the JSON stored in the {@code audit_log.entry} column,
 * with an uppercase {@code action} value as the type discriminator.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "action")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ApplicationCreated.class, name = "APPLICATION_CREATED"),
        @JsonSubTypes.Type(value = ScoringRun.class, name = "SCORING_RUN"),
        @JsonSubTypes.Type(value = ManualDecision.class, name = "MANUAL_DECISION")
})
public sealed interface AuditEntry permits ApplicationCreated, ScoringRun, ManualDecision {
}
