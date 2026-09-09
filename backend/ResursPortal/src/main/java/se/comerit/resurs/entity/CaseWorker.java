package se.comerit.resurs.entity;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Case worker account. {@link #name} and {@link #email} are stored at rest via
 * {@link PiiAttributeConverter} (encrypted base64 in production, plain strings
 * under the test profile). {@link #emailIndex} is a blind index (HMAC-SHA256 of
 * the canonicalized email) derived on persist by
 * {@link CaseWorkerBlindIndexListener} and used for equality lookups without
 * comparing the stored PII.
 */
@Entity
@Table(name = "case_workers")
@EntityListeners(CaseWorkerBlindIndexListener.class)
public class CaseWorker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = PiiAttributeConverter.class)
    @Column(length = 512)
    @NotBlank
    @Size(max = 512)
    private String name;

    @Convert(converter = PiiAttributeConverter.class)
    @Column(length = 512)
    @NotBlank
    @Email
    @Size(max = 512)
    private String email;

    @Column(name = "email_index", columnDefinition = "BYTEA", unique = true, nullable = false)
    private byte[] emailIndex;

    @Column(length = 255)
    @NotBlank
    @Size(max = 255)
    private String password;

    public CaseWorker(@Nonnull String name, @Nonnull String email, @Nonnull String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }

    protected CaseWorker() {
        // Constructor needed by JPA
    }

    @Nullable
    public Long getId() {
        return id;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public void setName(@Nonnull String name) {
        this.name = name;
    }

    @Nonnull
    public String getEmail() {
        return email;
    }

    public void setEmail(@Nonnull String email) {
        this.email = email;
    }

    @Nonnull
    public byte[] getEmailIndex() {
        return emailIndex;
    }

    public void setEmailIndex(@Nonnull byte[] emailIndex) {
        this.emailIndex = emailIndex;
    }

    @Nonnull
    public String getPassword() {
        return password;
    }

    public void setPassword(@Nonnull String password) {
        this.password = password;
    }
}
