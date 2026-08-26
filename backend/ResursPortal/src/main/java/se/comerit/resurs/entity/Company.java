package se.comerit.resurs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "org_number", length = 20, unique = true)
    private String orgNumber;
    @Column(name = "company_name", length = 200)
    private String name;
    @Column(name = "authorized_signatory", length = 100)
    private String authorizedSignatory;

    public Company() {
        // Empty constructor for JPA
    }

    public Long getId() {
        return id;
    }

    public String getOrgNumber() {
        return orgNumber;
    }

    public void setOrgNumber(String orgNumber) {
        this.orgNumber = orgNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthorizedSignatory() {
        return authorizedSignatory;
    }

    public void setAuthorizedSignatory(String authorizedSignatory) {
        this.authorizedSignatory = authorizedSignatory;
    }

}
