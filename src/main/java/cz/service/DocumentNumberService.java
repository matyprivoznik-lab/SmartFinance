package cz.service;

import cz.document.DocumentKind;
import cz.document.DocumentNumbers;
import cz.entity.Company;
import cz.entity.Invoice;
import cz.entity.User;
import cz.repository.InvoiceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class DocumentNumberService {

    private final InvoiceRepository invoiceRepository;

    public DocumentNumberService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public String next(User user, DocumentKind kind, LocalDate date) {
        return next(user, null, kind, date);
    }

    public String next(User user, Company company, DocumentKind kind, LocalDate date) {
        DocumentKind used = kind == null ? DocumentKind.INVOICE : kind;
        List<Invoice> source = company == null ? invoiceRepository.findByUser(user) : invoiceRepository.findByCompany(company);
        List<String> existing = source.stream()
                .filter(invoice -> invoice.getDocumentKind() == used)
                .map(Invoice::getInvoiceNumber)
                .toList();
        String pattern = company != null && company.getInvoiceNumberPattern() != null
                ? company.getInvoiceNumberPattern()
                : (user == null ? null : user.getInvoiceNumberPattern());
        return DocumentNumbers.next(pattern, used, date, existing);
    }
}
