package cz.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String ico;
    private String vatNumber;
    private String address;
    private String email;
    private String phone;
    private String riskInsolvency;
    @Column(length = 300)
    private String riskInsolvencyDetail;
    private Boolean riskUnreliableVat;
    private java.time.LocalDateTime riskCheckedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "client", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Invoice> invoices = new ArrayList<>();

    public Client() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIco() {
        return ico;
    }

    public void setIco(String ico) {
        this.ico = ico;
    }

    public String getVatNumber() {
        return vatNumber;
    }

    public void setVatNumber(String vatNumber) {
        this.vatNumber = vatNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRiskInsolvency() { return riskInsolvency; }
    public void setRiskInsolvency(String riskInsolvency) { this.riskInsolvency = riskInsolvency; }
    public String getRiskInsolvencyDetail() { return riskInsolvencyDetail; }
    public void setRiskInsolvencyDetail(String riskInsolvencyDetail) { this.riskInsolvencyDetail = riskInsolvencyDetail; }
    public Boolean getRiskUnreliableVat() { return riskUnreliableVat; }
    public void setRiskUnreliableVat(Boolean riskUnreliableVat) { this.riskUnreliableVat = riskUnreliableVat; }
    public java.time.LocalDateTime getRiskCheckedAt() { return riskCheckedAt; }
    public void setRiskCheckedAt(java.time.LocalDateTime riskCheckedAt) { this.riskCheckedAt = riskCheckedAt; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<Invoice> getInvoices() {
        return invoices;
    }

    public void setInvoices(List<Invoice> invoices) {
        this.invoices = invoices;
    }
}