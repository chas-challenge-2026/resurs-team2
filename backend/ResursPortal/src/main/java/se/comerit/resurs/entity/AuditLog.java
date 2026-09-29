package se.comerit.resurs.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.annotation.Nonnull;
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

/**
 * One entry in a per-application audit hash chain.
 *
 * <p>{@link #getSequenceNumber()} is unique and dense per application. The entry following
 * this one is the one with the next higher sequence number, and verification recomputes
 * each link from its predecessor, so no link to the preceding entry is held here.
 *
 * <p>{@link #getHash()} and {@link #getSignature()} are raw bytes, not text. The returned
 * arrays are the ones this entity holds; mutating them mutates persisted state.
 */
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

    /** Raw 32-byte SHA-256 chain hash of this entry. */
    @Column(nullable = false)
    @Nonnull
    private byte[] hash;

    /** Raw 64-byte Ed25519 signature over {@link #hash}. */
    @Column(nullable = false)
    @Nonnull
    private byte[] signature;

    @Convert(converter = PiiAttributeConverter.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    @Nonnull
    private String entry;

    @Column(nullable = false)
    private Instant timestamp;

    protected AuditLog() {
    }

    public AuditLog(@Nonnull Application application, long sequenceNumber, @Nonnull byte[] hash,
            @Nonnull byte[] signature, @Nonnull String entry) {
        this.application = application;
        this.sequenceNumber = sequenceNumber;
        this.hash = hash;
        this.signature = signature;
        this.entry = entry;
    }

    @PrePersist
    protected void onCreate() {
        timestamp = Instant.now();
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
    public byte[] getHash() {
        return hash;
    }

    @Nonnull
    public byte[] getSignature() {
        return signature;
    }

    @Nonnull
    public String getEntry() {
        return entry;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
