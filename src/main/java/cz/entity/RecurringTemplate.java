package cz.entity;

import cz.document.VatRegime;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "recurring_templates")
public class RecurringTemplate {

    public enum Interval {
        MONTHLY("Každý měsíc"),
        QUARTERLY("Každé čtvrtletí");

        private final String label;

        Interval(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    private String title;
    private String description;
    private Double quantity = 1.0;
    private Double unitPriceGross = 0.0;
    private Double vatRate = 21.0;

    @Enumerated(EnumType.STRING)
    private VatRegime vatRegime = VatRegime.PAYER;

    @Enumerated(EnumType.STRING)
    private Interval interval = Interval.MONTHLY;

    private LocalDate nextIssueDate;
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public Double getUnitPriceGross() { return unitPriceGross; }
    public void setUnitPriceGross(Double unitPriceGross) { this.unitPriceGross = unitPriceGross; }
    public Double getVatRate() { return vatRate; }
    public void setVatRate(Double vatRate) { this.vatRate = vatRate; }
    public VatRegime getVatRegime() { return vatRegime; }
    public void setVatRegime(VatRegime vatRegime) { this.vatRegime = vatRegime; }
    public Interval getInterval() { return interval; }
    public void setInterval(Interval interval) { this.interval = interval; }
    public LocalDate getNextIssueDate() { return nextIssueDate; }
    public void setNextIssueDate(LocalDate nextIssueDate) { this.nextIssueDate = nextIssueDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
