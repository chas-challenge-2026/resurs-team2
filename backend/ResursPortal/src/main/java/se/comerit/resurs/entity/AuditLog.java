package se.comerit.resurs.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @Nonnull
    private Application application;

    @Column(nullable = false)
    private long sequenceNumber;

    @Column(nullable = false)
    @Nonnull
    private String hash;

    @Column(nullable = false)
    @Nonnull
    private String previousHash;

    @Convert(converter = PiiAttributeConverter.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    @Nonnull
    private String entry;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    protected AuditLog() {}

    public AuditLog(@Nonnull Application application, long sequenceNumber, @Nonnull String hash,
                    @Nullable String previousHash, @Nonnull String entry) {
        this.application = application;
        this.sequenceNumber = sequenceNumber;
        this.hash = hash;
        this.previousHash = previousHash != null ? previousHash : "";
        this.entry = entry;
    }

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now(ZoneId.of("UTC"));
    }

    public UUID getId() {
        return id;
    }

    @Nonnull
    public Application getApplication() {
        return application;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Nonnull
    public String getHash() {
        return hash;
    }

    @Nonnull
    public String getPreviousHash() {
        return previousHash;
    }

    @Nonnull
    public String getEntry() {
        return entry;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
