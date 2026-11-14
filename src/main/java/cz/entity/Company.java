package cz.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    private String name;
    private String ico;
    private String vatNumber;

    @Column(length = 500)
    private String address;

    private String bankAccount;
    private String iban;
    private String swift;

    @Column(length = 24)
    private String invoiceNumberPattern;

    private boolean vatPayer;
    private double expenseRate = 60.0;
    private boolean mainActivity = true;

    private String fioApiToken;
    private boolean remindersEnabled = true;
    private int gentleReminderDays = 3;
    private int formalReminderDays = 14;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIco() { return ico; }
    public void setIco(String ico) { this.ico = ico; }
    public String getVatNumber() { return vatNumber; }
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    public String getSwift() { return swift; }
    public void setSwift(String swift) { this.swift = swift; }
    public String getInvoiceNumberPattern() { return invoiceNumberPattern; }
    public void setInvoiceNumberPattern(String invoiceNumberPattern) { this.invoiceNumberPattern = invoiceNumberPattern; }
    public boolean isVatPayer() { return vatPayer; }
    public void setVatPayer(boolean vatPayer) { this.vatPayer = vatPayer; }
    public double getExpenseRate() { return expenseRate; }
    public void setExpenseRate(double expenseRate) { this.expenseRate = expenseRate; }
    public boolean isMainActivity() { return mainActivity; }
    public void setMainActivity(boolean mainActivity) { this.mainActivity = mainActivity; }
    public String getFioApiToken() { return fioApiToken; }
    public void setFioApiToken(String fioApiToken) { this.fioApiToken = fioApiToken; }
    public boolean isRemindersEnabled() { return remindersEnabled; }
    public void setRemindersEnabled(boolean remindersEnabled) { this.remindersEnabled = remindersEnabled; }
    public int getGentleReminderDays() { return gentleReminderDays; }
    public void setGentleReminderDays(int gentleReminderDays) { this.gentleReminderDays = gentleReminderDays; }
    public int getFormalReminderDays() { return formalReminderDays; }
    public void setFormalReminderDays(int formalReminderDays) { this.formalReminderDays = formalReminderDays; }
}
