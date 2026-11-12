package cz.service;

import cz.entity.Invoice;
import cz.entity.User;
import cz.repository.InvoiceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TaxService {

    private final InvoiceRepository invoiceRepository;
    private static final BigDecimal TAX_DISCOUNT_BASIC = new BigDecimal("30840");

    public TaxService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public TaxSummary calculateTaxSummary(User user, int year, boolean isStudent, boolean isEmployed) {
        List<Invoice> userInvoices = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getUser() != null && inv.getUser().getId().equals(user.getId()))
                .filter(inv -> inv.getIssueDate() != null && inv.getIssueDate().getYear() == year)
                .toList();

        boolean vatPayer = user.isVatPayer();
        BigDecimal taxableRevenue = userInvoices.stream()
                .map(inv -> {
                    double amount = vatPayer
                            ? (inv.getNetAmount() != null ? inv.getNetAmount() : 0.0)
                            : (inv.getGrossTotal() != null ? inv.getGrossTotal() : 0.0);
                    return BigDecimal.valueOf(amount);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal flatExpenseRate = BigDecimal.valueOf(user.getExpenseRate() > 1
                ? user.getExpenseRate() / 100.0
                : user.getExpenseRate());
        BigDecimal flatExpenses = taxableRevenue.multiply(flatExpenseRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxFlatExpense = new BigDecimal("1200000");
        if (flatExpenses.compareTo(maxFlatExpense) > 0) {
            flatExpenses = maxFlatExpense;
        }

        BigDecimal incomeTaxBase = taxableRevenue.subtract(flatExpenses).max(BigDecimal.ZERO);
        BigDecimal incomeTaxBeforeCredit = incomeTaxBase.multiply(new BigDecimal("0.15")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal incomeTaxDue = incomeTaxBeforeCredit.subtract(TAX_DISCOUNT_BASIC).max(BigDecimal.ZERO);

        BigDecimal insuranceAssessmentBase = incomeTaxBase.multiply(new BigDecimal("0.50"));

        boolean isSecondary = isStudent || isEmployed || !user.isMainActivity();
        BigDecimal socialInsurance = BigDecimal.ZERO;
        BigDecimal secondaryThreshold = new BigDecimal("105520");

        if (!isSecondary || incomeTaxBase.compareTo(secondaryThreshold) > 0) {
            socialInsurance = insuranceAssessmentBase.multiply(new BigDecimal("0.292")).setScale(0, RoundingMode.HALF_UP);
        }

        BigDecimal healthInsurance = insuranceAssessmentBase.multiply(new BigDecimal("0.135")).setScale(0, RoundingMode.HALF_UP);

        return new TaxSummary(
                year,
                taxableRevenue,
                flatExpenses,
                incomeTaxBase,
                incomeTaxBeforeCredit,
                TAX_DISCOUNT_BASIC,
                incomeTaxDue,
                socialInsurance,
                healthInsurance,
                isStudent,
                isEmployed
        );
    }

    public static class TaxSummary {
        private final int year;
        private final BigDecimal taxableRevenue;
        private final BigDecimal flatExpenses;
        private final BigDecimal incomeTaxBase;
        private final BigDecimal incomeTaxBeforeCredit;
        private final BigDecimal taxDiscount;
        private final BigDecimal incomeTaxDue;
        private final BigDecimal socialInsurance;
        private final BigDecimal healthInsurance;
        private final boolean student;
        private final boolean employed;

        public TaxSummary(int year, BigDecimal taxableRevenue, BigDecimal flatExpenses, BigDecimal incomeTaxBase,
                          BigDecimal incomeTaxBeforeCredit, BigDecimal taxDiscount, BigDecimal incomeTaxDue,
                          BigDecimal socialInsurance, BigDecimal healthInsurance,
                          boolean student, boolean employed) {
            this.year = year;
            this.taxableRevenue = taxableRevenue;
            this.flatExpenses = flatExpenses;
            this.incomeTaxBase = incomeTaxBase;
            this.incomeTaxBeforeCredit = incomeTaxBeforeCredit;
            this.taxDiscount = taxDiscount;
            this.incomeTaxDue = incomeTaxDue;
            this.socialInsurance = socialInsurance;
            this.healthInsurance = healthInsurance;
            this.student = student;
            this.employed = employed;
        }

        public int getYear() { return year; }
        public BigDecimal getTaxableRevenue() { return taxableRevenue; }
        public BigDecimal getFlatExpenses() { return flatExpenses; }
        public BigDecimal getIncomeTaxBase() { return incomeTaxBase; }
        public BigDecimal getIncomeTaxBeforeCredit() { return incomeTaxBeforeCredit; }
        public BigDecimal getTaxDiscount() { return taxDiscount; }
        public BigDecimal getIncomeTaxDue() { return incomeTaxDue; }
        public BigDecimal getSocialInsurance() { return socialInsurance; }
        public BigDecimal getHealthInsurance() { return healthInsurance; }
        public boolean isStudent() { return student; }
        public boolean isEmployed() { return employed; }
    }
}
