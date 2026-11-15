package cz.service;

import cz.document.DocumentKind;
import cz.entity.Company;
import cz.entity.Expense;
import cz.entity.Invoice;
import cz.repository.ExpenseRepository;
import cz.repository.InvoiceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TurnoverService {

    public static final double VAT_LIMIT = 2_000_000.0;

    private final InvoiceRepository invoiceRepository;
    private final ExpenseRepository expenseRepository;

    public TurnoverService(InvoiceRepository invoiceRepository, ExpenseRepository expenseRepository) {
        this.invoiceRepository = invoiceRepository;
        this.expenseRepository = expenseRepository;
    }

    public double lastTwelveMonthsNet(Company company, LocalDate today) {
        if (company == null) {
            return 0.0;
        }
        LocalDate from = today.minusMonths(12);
        double sum = 0.0;
        for (Invoice invoice : invoiceRepository.findByCompany(company)) {
            if (invoice.getDocumentKind() != DocumentKind.INVOICE || invoice.getIssueDate() == null) {
                continue;
            }
            if (invoice.getIssueDate().isBefore(from) || invoice.getIssueDate().isAfter(today)) {
                continue;
            }
            double net = invoice.getCzkNetAmount() != null ? invoice.getCzkNetAmount() : (invoice.getNetAmount() == null ? 0.0 : invoice.getNetAmount());
            sum += net;
        }
        return Math.round(sum * 100.0) / 100.0;
    }

    public double expenses(Company company) {
        double sum = 0.0;
        List<Expense> items = company == null ? List.of() : expenseRepository.findByCompanyOrderByIssueDateDesc(company);
        for (Expense expense : items) {
            sum += expense.getGrossTotal() == null ? 0.0 : expense.getGrossTotal();
        }
        return Math.round(sum * 100.0) / 100.0;
    }
}
