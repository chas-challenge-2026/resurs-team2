package se.comerit.resurs.entity;

import java.util.UUID;
import java.time.Instant;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;



@Entity
@Table(name = "documents")
public class Document {
    @Id
    @Column(name = "uuid", nullable = false)

    private UUID uuid = UUID.randomUUID();

    @ManyToOne
    @NotNull
    private Application application;

    @Convert(converter = PiiAttributeConverter.class)
    @Column(length = 512)
    @NotBlank
    @Size(max = 512)
    private String filename;

    @Convert(converter = PiiAttributeConverter.class)
    @Column(name = "original_filename", length = 512)
    @NotBlank
    @Size(max = 512)
    private String originalFilename;

    @Column(name = "doc_type", length = 50)
    @NotBlank
    @Size(max = 50)
    private String docType;

    @Column(name = "uploaded_at")
    @Nullable
    private Instant uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        uploadedAt = Instant.now();
    }

    public Document(@Nonnull Application application, @Nonnull String originalFilename, @Nonnull String docType) {
        this.application = application;
        // Storage key; DocumentService overwrites it with the opaque <uuid>.pdf key after upload.
        this.filename = originalFilename;
        this.originalFilename = originalFilename;
        this.docType = docType;
    }

    protected Document() {
        // Constructor needed by JPA
    }

    @Nullable
    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(@Nonnull UUID uuid) {this.uuid = uuid;
    }

    @Nonnull
    public Application getApplication() {
        return application;
    }

    public void setApplication(@Nonnull Application application) {
        this.application = application;
    }

    @Nonnull
    public String getFilename() {
        return filename;
    }

    public void setFilename(@Nonnull String filename) {
        this.filename = filename;
    }

    @Nonnull
    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(@Nonnull String originalFilename) {
        this.originalFilename = originalFilename;
    }

    @Nonnull
    public String getDocType() {
        return docType;
    }

    public void setDocType(@Nonnull String docType) {
        this.docType = docType;
    }

    @Nullable
    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
