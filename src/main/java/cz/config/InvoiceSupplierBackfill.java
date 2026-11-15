package cz.config;

import cz.entity.Invoice;
import cz.repository.InvoiceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(80)
public class InvoiceSupplierBackfill implements CommandLineRunner {

    private final InvoiceRepository invoiceRepository;

    public InvoiceSupplierBackfill(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Invoice> missing = invoiceRepository.findMissingSupplierSnapshot();
        for (Invoice invoice : missing) {
            if (invoice.getUser() == null) {
                continue;
            }
            if (invoice.getSupplierName() == null) {
                invoice.captureSupplier(invoice.getUser());
            }
            if (invoice.getSupplierName() == null) {
                invoice.setSupplierName("");
            }
            if (invoice.getCustomerName() == null) {
                invoice.captureCustomer(invoice.getClient());
            }
            if (invoice.getCustomerName() == null) {
                invoice.setCustomerName("");
            }
            invoiceRepository.save(invoice);
        }
    }
}
