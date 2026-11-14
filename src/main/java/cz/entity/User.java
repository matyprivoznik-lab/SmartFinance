package cz.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String password;
    private String name;
    private String role = "ROLE_USER";
    private boolean enabled = true;

    // Identifikační a kontaktní údaje
    private String ico;
    private String vatNumber;
    private String street;
    private String city;
    private String zipCode;
    private String address;
    private String phone;

    // Bankovní údaje (QR Platba & Faktura)
    private String bankAccount;
    private String iban;
    private String swift;

    // Daňové preference pro OSVČ
    private boolean vatPayer = false;
    private double expenseRate = 0.60; // Výdajový paušál (např. 60 %)
    private boolean mainActivity = true; // Hlavní vs. vedlejší činnost

    // Patka faktury / poznámka
    @Column(length = 1000)
    private String note;

    // Dvoufázové ověření (MFA)
    private boolean mfaEnabled = false;
    private String mfaSecret;

    // Souhlasy. Sloupce jsou nullable, aby šly doplnit k už existujícím účtům.
    @Column(columnDefinition = "boolean default false")
    private Boolean termsAccepted = Boolean.FALSE;

    @Column(columnDefinition = "boolean default false")
    private Boolean privacyAccepted = Boolean.FALSE;

    private LocalDateTime termsAcceptedAt;
    private LocalDateTime privacyAcceptedAt;

    @Column(columnDefinition = "boolean default false")
    private Boolean marketingConsent = Boolean.FALSE;

    private LocalDateTime marketingConsentAt;

    public User() {
    }

    public User(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public User(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
    }

    public User(String email, String password, String name, String role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
    }

    // --- GETTERY A SETTERY ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getIco() { return ico; }
    public void setIco(String ico) { this.ico = ico; }

    public String getVatNumber() { return vatNumber; }
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getAddress() {
        if (address != null && !address.isBlank()) return address;
        return (street != null ? street : "") + (city != null ? ", " + city : "");
    }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }

    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }

    public String getSwift() { return swift; }
    public void setSwift(String swift) { this.swift = swift; }

    public boolean isVatPayer() { return vatPayer; }
    public void setVatPayer(boolean vatPayer) { this.vatPayer = vatPayer; }

    public double getExpenseRate() { return expenseRate; }
    public void setExpenseRate(double expenseRate) { this.expenseRate = expenseRate; }

    public boolean isMainActivity() { return mainActivity; }
    public void setMainActivity(boolean mainActivity) { this.mainActivity = mainActivity; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }

    public String getMfaSecret() { return mfaSecret; }
    public void setMfaSecret(String mfaSecret) { this.mfaSecret = mfaSecret; }

    public boolean isTermsAccepted() { return Boolean.TRUE.equals(termsAccepted); }
    public void setTermsAccepted(boolean termsAccepted) { this.termsAccepted = termsAccepted; }

    public boolean isPrivacyAccepted() { return Boolean.TRUE.equals(privacyAccepted); }
    public void setPrivacyAccepted(boolean privacyAccepted) { this.privacyAccepted = privacyAccepted; }

    public LocalDateTime getTermsAcceptedAt() { return termsAcceptedAt; }
    public void setTermsAcceptedAt(LocalDateTime termsAcceptedAt) { this.termsAcceptedAt = termsAcceptedAt; }

    public LocalDateTime getPrivacyAcceptedAt() { return privacyAcceptedAt; }
    public void setPrivacyAcceptedAt(LocalDateTime privacyAcceptedAt) { this.privacyAcceptedAt = privacyAcceptedAt; }

    public boolean isMarketingConsent() { return Boolean.TRUE.equals(marketingConsent); }
    public void setMarketingConsent(boolean marketingConsent) { this.marketingConsent = marketingConsent; }

    public LocalDateTime getMarketingConsentAt() { return marketingConsentAt; }
    public void setMarketingConsentAt(LocalDateTime marketingConsentAt) { this.marketingConsentAt = marketingConsentAt; }
}