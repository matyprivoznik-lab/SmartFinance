package cz.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "invoice_items")
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;
    private Double quantity;

    @Column(name = "unit_price")
    private Double unitPriceGross;

    @Column(name = "vat_rate")
    private Double vatRate;

    @Column(name = "subtotal")
    private Double netAmount;

    @Column(name = "total")
    private Double lineGrossTotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    public InvoiceItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public Double getUnitPriceGross() { return unitPriceGross; }
    public void setUnitPriceGross(Double unitPriceGross) { this.unitPriceGross = unitPriceGross; }

    public Double getVatRate() { return vatRate; }
    public void setVatRate(Double vatRate) { this.vatRate = vatRate; }

    public Double getNetAmount() { return netAmount; }
    public void setNetAmount(Double netAmount) { this.netAmount = netAmount; }

    public Double getLineGrossTotal() { return lineGrossTotal; }
    public void setLineGrossTotal(Double lineGrossTotal) { this.lineGrossTotal = lineGrossTotal; }

    public Double getVatAmount() {
        if (netAmount != null && lineGrossTotal != null) {
            return Math.round((lineGrossTotal - netAmount) * 100.0) / 100.0;
        }
        return 0.0;
    }

    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }
}
