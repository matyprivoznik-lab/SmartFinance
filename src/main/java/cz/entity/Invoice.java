package cz.entity;

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

    public LocalDate getTaxableSupplyDate() { return taxableSupplyDate; }
    public void setTaxableSupplyDate(LocalDate taxableSupplyDate) { this.taxableSupplyDate = taxableSupplyDate; }

    public Double getGrossTotal() {
        if (grossTotal != null && grossTotal > 0.0) {
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
        if (vatAmount != null && vatAmount > 0.0) {
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
