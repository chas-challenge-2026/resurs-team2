package se.comerit.resurs.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "applications")
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @NotNull
    private Company company;

    @Convert(converter = AmountAttributeConverter.class)
    @Column(name = "requested_amount", length = 512)
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal requestedAmount;

    @Convert(converter = PiiAttributeConverter.class)
    @Column(name = "purpose", length = 512)
    @Nonnull
    private String purpose;

    @Column(length = 30)
    @Enumerated(EnumType.STRING)
    @Nonnull
    private ApplicationStatus status = ApplicationStatus.PENDING_DOCS;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    @Nullable
    private Decision decision;

    @Convert(converter = PiiAttributeConverter.class)
    @Column(name = "decision_reason", columnDefinition = "TEXT")
    @Nullable
    private String decisionReason;

    @Convert(converter = PiiAttributeConverter.class)
    @Column(name = "scoring_result", columnDefinition = "TEXT")
    @Nullable
    private String scoringResult;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "application")
    @OrderBy("uploadedAt DESC")
    private List<Document> documents;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "application")
    @OrderBy("timestamp DESC")
    private List<AuditLog> auditLogs;

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

    public Application(@Nonnull Company company, @Nonnull BigDecimal requestedAmount,
            @Nonnull String purpose, ApplicationStatus statusValue, Decision decision,
            String decisionReason, String scoringResult) {
        this.company = company;
        this.requestedAmount = requestedAmount;
        this.purpose = purpose;
        this.status = statusValue;
        this.decision = decision;
        this.decisionReason = decisionReason;
        this.scoringResult = scoringResult;
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
    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(@Nonnull ApplicationStatus status) {
        this.status = status;
    }

    @Nullable
    public Decision getDecision() {
        return decision;
    }

    public void setDecision(@Nullable Decision decision) {
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
    public List<Document> getDocuments() {
        return documents;
    }

    @Nonnull
    public List<AuditLog> getAuditLogs() {
        return auditLogs;
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
