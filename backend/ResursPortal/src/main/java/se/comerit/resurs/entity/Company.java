package se.comerit.resurs.entity;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "companies")
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "org_number", length = 20, unique = true)
    @NotBlank
    @Size(max = 20)
    private String orgNumber;
    @Column(name = "company_name", length = 200)
    @NotBlank
    @Size(max = 200)
    private String name;
    @Column(name = "authorized_signatory", length = 100)
    @NotBlank
    @Size(max = 100)
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
