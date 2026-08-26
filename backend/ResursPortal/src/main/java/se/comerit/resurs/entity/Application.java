package se.comerit.resurs.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "applications")
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @NotNull
    private Company company;
    @Column(name = "requested_amount")
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal requestedAmount;
    @Column(columnDefinition = "TEXT")
    @NotBlank
    private String purpose;
    @Column(length = 30)
    @NotBlank
    @Size(max = 30)
    private String status = "PENDING_DOCS";
    @Column(length = 20)
    @Nullable
    @Size(max = 20)
    private String decision;
    @Column(name = "decision_reason", columnDefinition = "TEXT")
    @Nullable
    private String decisionReason;
    @Column(name = "scoring_result", columnDefinition = "TEXT")
    @Nullable
    private String scoringResult;
    @Column(name = "audit_log", columnDefinition = "TEXT")
    @NotBlank
    private String auditLog = "[]";
    @Column(name = "created_at")
    @Nullable
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    @Nullable
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.of("UTC"));
        updatedAt = LocalDateTime.now(ZoneId.of("UTC"));
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.of("UTC"));
    }

    public Application(@Nonnull Company company, @Nonnull BigDecimal requestedAmount, @Nonnull String purpose) {
        this.company = company;
        this.requestedAmount = requestedAmount;
        this.purpose = purpose;
    }

    protected Application() {
        // Constructor needed by JPA
    }

    @Nullable
    public Long getId() {
        return id;
    }

    @Nonnull
    public Company getCompany() {
        return company;
    }

    public void setCompany(@Nonnull Company company) {
        this.company = company;
    }

    @Nonnull
    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(@Nonnull BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    @Nonnull
    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(@Nonnull String purpose) {
        this.purpose = purpose;
    }

    @Nonnull
    public String getStatus() {
        return status;
    }

    public void setStatus(@Nonnull String status) {
        this.status = status;
    }

    @Nullable
    public String getDecision() {
        return decision;
    }

    public void setDecision(@Nullable String decision) {
        this.decision = decision;
    }

    @Nullable
    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(@Nullable String decisionReason) {
        this.decisionReason = decisionReason;
    }

    @Nullable
    public String getScoringResult() {
        return scoringResult;
    }

    public void setScoringResult(@Nullable String scoringResult) {
        this.scoringResult = scoringResult;
    }

    @Nonnull
    public String getAuditLog() {
        return auditLog;
    }

    public void setAuditLog(@Nonnull String auditLog) {
        this.auditLog = auditLog;
    }

    @Nullable
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
