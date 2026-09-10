package se.comerit.resurs.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.UuidGenerator;


@Entity
@Table(name = "documents")
public class Document {
    @Id
    @UuidGenerator
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long uuid;
    @ManyToOne
    @NotNull
    private Application application;

    @Convert(converter = PiiAttributeConverter.class)
    @Column(length = 512)
    @NotBlank
    @Size(max = 512)
    private String filename;

    @Column(name = "doc_type", length = 50)
    @NotBlank
    @Size(max = 50)
    private String docType;

    @Column(name = "uploaded_at")
    @Nullable
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now(ZoneId.of("UTC"));
    }

    public Document(@Nonnull Application application, @Nonnull String filename, @Nonnull String docType) {
        this.application = application;
        this.filename = filename;
        this.docType = docType;
    }

    protected Document() {
        // Constructor needed by JPA
    }

    @Nullable
    public Long getUuid() {return uuid;
    }

    public void setUuid(@Nonnull Long uuid) {this.uuid = uuid;
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
    public String getDocType() {
        return docType;
    }

    public void setDocType(@Nonnull String docType) {
        this.docType = docType;
    }

    @Nullable
    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
}
