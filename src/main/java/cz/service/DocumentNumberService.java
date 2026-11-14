package cz.service;

import cz.document.DocumentKind;
import cz.document.DocumentNumbers;
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
        DocumentKind used = kind == null ? DocumentKind.INVOICE : kind;
        List<String> existing = invoiceRepository.findByUser(user).stream()
                .filter(invoice -> invoice.getDocumentKind() == used)
                .map(Invoice::getInvoiceNumber)
                .toList();
        return DocumentNumbers.next(user == null ? null : user.getInvoiceNumberPattern(), used, date, existing);
    }
}
