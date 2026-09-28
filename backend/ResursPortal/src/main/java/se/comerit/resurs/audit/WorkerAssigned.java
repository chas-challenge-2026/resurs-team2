package se.comerit.resurs.audit;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkerAssigned(@JsonProperty("workerId") Long workerId,
                             @JsonProperty("worker") String worker) implements AuditEntry {
}