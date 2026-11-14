package cz.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDate issueDate;
    private String supplierName;
    private String supplierIco;
    private String documentNumber;
    private Double netAmount = 0.0;
    private Double vatAmount = 0.0;
    private Double grossTotal = 0.0;
    private String note;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getSupplierIco() { return supplierIco; }
    public void setSupplierIco(String supplierIco) { this.supplierIco = supplierIco; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public Double getNetAmount() { return netAmount; }
    public void setNetAmount(Double netAmount) { this.netAmount = netAmount; }
    public Double getVatAmount() { return vatAmount; }
    public void setVatAmount(Double vatAmount) { this.vatAmount = vatAmount; }
    public Double getGrossTotal() { return grossTotal; }
    public void setGrossTotal(Double grossTotal) { this.grossTotal = grossTotal; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
