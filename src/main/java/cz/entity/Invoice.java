package cz.entity;

import cz.document.DocumentKind;
import cz.document.VatRegime;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String invoiceNumber;

    private String variableSymbol;
    private String constantSymbol;
    private String paymentMethod = "Bankovní převod";

    private LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDate paidAt;
    private LocalDate gentleReminderSentAt;
    private LocalDate formalReminderSentAt;

    @Column(unique = true, length = 64)
    private String publicToken;

    @Column(name = "duzp")
    private LocalDate taxableSupplyDate;

    @Column(name = "total_price")
    private Double grossTotal = 0.0;

    @Column(name = "tax_base")
    private Double netAmount = 0.0;

    @Column(name = "tax_amount")
    private Double vatAmount = 0.0;

    private Integer vatRate = 0;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Enumerated(EnumType.STRING)
    private DocumentKind documentKind;

    @Enumerated(EnumType.STRING)
    private VatRegime vatRegime;

    private String currency = "CZK";
    private Double exchangeRate;
    private LocalDate exchangeRateDate;
    private Double czkNetAmount;
    private Double czkVatAmount;
    private Double czkGrossTotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private Invoice sourceDocument;

    @Column(length = 255)
    private String supplierName;

    @Column(length = 500)
    private String supplierAddress;

    private String supplierIco;
    private String supplierVatNumber;

    @Column(length = 255)
    private String customerName;

    @Column(length = 500)
    private String customerAddress;

    private String customerIco;
    private String customerVatNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    public enum InvoiceStatus {
        ISSUED, PAID, OVERDUE, CANCELLED
    }

    public Invoice() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getVariableSymbol() { return variableSymbol; }
    public void setVariableSymbol(String variableSymbol) { this.variableSymbol = variableSymbol; }

    public String getConstantSymbol() { return constantSymbol; }
    public void setConstantSymbol(String constantSymbol) { this.constantSymbol = constantSymbol; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDate paidAt) { this.paidAt = paidAt; }
    public LocalDate getGentleReminderSentAt() { return gentleReminderSentAt; }
    public void setGentleReminderSentAt(LocalDate gentleReminderSentAt) { this.gentleReminderSentAt = gentleReminderSentAt; }
    public LocalDate getFormalReminderSentAt() { return formalReminderSentAt; }
    public void setFormalReminderSentAt(LocalDate formalReminderSentAt) { this.formalReminderSentAt = formalReminderSentAt; }
    public String getPublicToken() { return publicToken; }
    public void setPublicToken(String publicToken) { this.publicToken = publicToken; }

    public LocalDate getTaxableSupplyDate() { return taxableSupplyDate; }
    public void setTaxableSupplyDate(LocalDate taxableSupplyDate) { this.taxableSupplyDate = taxableSupplyDate; }

    public Double getGrossTotal() {
        if (grossTotal != null && grossTotal != 0.0) {
            return grossTotal;
        }
        if (items != null && !items.isEmpty()) {
            return items.stream()
                    .mapToDouble(item -> item.getLineGrossTotal() != null ? item.getLineGrossTotal() : 0.0)
                    .sum();
        }
        return grossTotal != null ? grossTotal : 0.0;
    }

    public void setGrossTotal(Double grossTotal) { this.grossTotal = grossTotal; }
    public void setGrossTotal(double grossTotal) { this.grossTotal = grossTotal; }
    public void setGrossTotal(BigDecimal grossTotal) {
        this.grossTotal = grossTotal != null ? grossTotal.doubleValue() : 0.0;
    }

    public Double getNetAmount() { return netAmount; }
    public void setNetAmount(Double netAmount) { this.netAmount = netAmount; }
    public void setNetAmount(double netAmount) { this.netAmount = netAmount; }
    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount != null ? netAmount.doubleValue() : 0.0;
    }

    public Double getVatAmount() {
        if (vatAmount != null && vatAmount != 0.0) {
            return vatAmount;
        }
        if (netAmount != null && grossTotal != null) {
            return Math.round((grossTotal - netAmount) * 100.0) / 100.0;
        }
        return vatAmount != null ? vatAmount : 0.0;
    }

    public void setVatAmount(Double vatAmount) { this.vatAmount = vatAmount; }

    public Integer getVatRate() { return vatRate; }
    public void setVatRate(Integer vatRate) { this.vatRate = vatRate; }

    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getSupplierAddress() { return supplierAddress; }
    public void setSupplierAddress(String supplierAddress) { this.supplierAddress = supplierAddress; }

    public String getSupplierIco() { return supplierIco; }
    public void setSupplierIco(String supplierIco) { this.supplierIco = supplierIco; }

    public String getSupplierVatNumber() { return supplierVatNumber; }
    public void setSupplierVatNumber(String supplierVatNumber) { this.supplierVatNumber = supplierVatNumber; }

    public void captureSupplier(User user) {
        if (user == null) {
            return;
        }
        this.supplierName = user.getName();
        this.supplierAddress = user.getAddress();
        this.supplierIco = user.getIco();
        this.supplierVatNumber = user.getVatNumber();
    }

    public void copySupplierFrom(Invoice stored) {
        if (stored == null) {
            return;
        }
        this.supplierName = stored.supplierName;
        this.supplierAddress = stored.supplierAddress;
        this.supplierIco = stored.supplierIco;
        this.supplierVatNumber = stored.supplierVatNumber;
    }

    public DocumentKind getDocumentKind() {
        return documentKind == null ? DocumentKind.INVOICE : documentKind;
    }

    public void setDocumentKind(DocumentKind documentKind) {
        this.documentKind = documentKind;
    }

    public VatRegime getVatRegime() {
        return vatRegime;
    }

    public void setVatRegime(VatRegime vatRegime) {
        this.vatRegime = vatRegime;
    }

    public String getCurrency() { return currency == null || currency.isBlank() ? "CZK" : currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Double getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(Double exchangeRate) { this.exchangeRate = exchangeRate; }
    public LocalDate getExchangeRateDate() { return exchangeRateDate; }
    public void setExchangeRateDate(LocalDate exchangeRateDate) { this.exchangeRateDate = exchangeRateDate; }
    public Double getCzkNetAmount() { return czkNetAmount; }
    public void setCzkNetAmount(Double czkNetAmount) { this.czkNetAmount = czkNetAmount; }
    public Double getCzkVatAmount() { return czkVatAmount; }
    public void setCzkVatAmount(Double czkVatAmount) { this.czkVatAmount = czkVatAmount; }
    public Double getCzkGrossTotal() { return czkGrossTotal; }
    public void setCzkGrossTotal(Double czkGrossTotal) { this.czkGrossTotal = czkGrossTotal; }
    public Invoice getSourceDocument() { return sourceDocument; }
    public void setSourceDocument(Invoice sourceDocument) { this.sourceDocument = sourceDocument; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }
    public String getCustomerIco() { return customerIco; }
    public void setCustomerIco(String customerIco) { this.customerIco = customerIco; }
    public String getCustomerVatNumber() { return customerVatNumber; }
    public void setCustomerVatNumber(String customerVatNumber) { this.customerVatNumber = customerVatNumber; }

    public void captureCustomer(Client client) {
        if (client == null) {
            return;
        }
        this.customerName = client.getName();
        this.customerAddress = client.getAddress();
        this.customerIco = client.getIco();
        this.customerVatNumber = client.getVatNumber();
    }

    public void copyCustomerFrom(Invoice stored) {
        if (stored == null) {
            return;
        }
        this.customerName = stored.customerName;
        this.customerAddress = stored.customerAddress;
        this.customerIco = stored.customerIco;
        this.customerVatNumber = stored.customerVatNumber;
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public List<InvoiceItem> getItems() { return items; }
    public void setItems(List<InvoiceItem> items) { this.items = items; }

    public void addItem(InvoiceItem item) {
        items.add(item);
        item.setInvoice(this);
    }
}
