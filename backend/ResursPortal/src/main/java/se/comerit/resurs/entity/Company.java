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

/**
 * Registered company. PII fields are stored at rest via
 * {@link PiiAttributeConverter} (encrypted base64 in production, plain strings
 * under the test profile). {@link #orgNumberIndex} is a blind index
 * (HMAC-SHA256 of the canonicalized org number) derived on persist by
 * {@link CompanyBlindIndexListener} and used for equality lookups without
 * comparing the stored PII.
 */
@Entity
@Table(name = "companies")
@EntityListeners(CompanyBlindIndexListener.class)
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Convert(converter = PiiAttributeConverter.class)
    @Column(name = "org_number", length = 512)
    @Nonnull
    private String orgNumber;
    @Column(name = "org_number_index", columnDefinition = "BYTEA", unique = true, nullable = false)
    private byte[] orgNumberIndex;
    @Convert(converter = PiiAttributeConverter.class)
    @Column(name = "company_name", length = 512)
    @Nonnull
    private String name;
    @Convert(converter = PiiAttributeConverter.class)
    @Column(name = "authorized_signatory", length = 512)
    @Nonnull
    private String authorizedSignatory;

    public Company(@Nonnull String orgNumber, @Nonnull String name, @Nonnull String authorizedSignatory) {
        this.orgNumber = orgNumber;
        this.name = name;
        this.authorizedSignatory = authorizedSignatory;
    }

    protected Company() {
        // Constructor needed by JPA
    }

    @Nullable
    public Long getId() {
        return id;
    }

    @Nonnull
    public String getOrgNumber() {
        return orgNumber;
    }

    public void setOrgNumber(@Nonnull String orgNumber) {
        this.orgNumber = orgNumber;
    }

    @Nonnull
    public byte[] getOrgNumberIndex() {
        return orgNumberIndex;
    }

    public void setOrgNumberIndex(@Nonnull byte[] orgNumberIndex) {
        this.orgNumberIndex = orgNumberIndex;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public void setName(@Nonnull String name) {
        this.name = name;
    }

    @Nonnull
    public String getAuthorizedSignatory() {
        return authorizedSignatory;
    }

    public void setAuthorizedSignatory(@Nonnull String authorizedSignatory) {
        this.authorizedSignatory = authorizedSignatory;
    }
}
