package cz.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_connections")
public class BankConnection {

    public static final String PENDING = "PENDING";
    public static final String LINKED = "LINKED";
    public static final String ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    private String institutionId;
    private String institutionName;
    private String requisitionId;

    @Column(unique = true, length = 64)
    private String reference;

    private String status = PENDING;

    @Column(length = 1000)
    private String accountIds;

    @Column(length = 300)
    private String statusDetail;

    private LocalDateTime linkedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getInstitutionId() { return institutionId; }
    public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
    public String getInstitutionName() { return institutionName; }
    public void setInstitutionName(String institutionName) { this.institutionName = institutionName; }
    public String getRequisitionId() { return requisitionId; }
    public void setRequisitionId(String requisitionId) { this.requisitionId = requisitionId; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAccountIds() { return accountIds; }
    public void setAccountIds(String accountIds) { this.accountIds = accountIds; }
    public String getStatusDetail() { return statusDetail; }
    public void setStatusDetail(String statusDetail) { this.statusDetail = statusDetail; }
    public LocalDateTime getLinkedAt() { return linkedAt; }
    public void setLinkedAt(LocalDateTime linkedAt) { this.linkedAt = linkedAt; }

    public boolean isLinked() {
        return LINKED.equals(status);
    }
}
