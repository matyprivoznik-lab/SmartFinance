package cz.repository;

import cz.document.DocumentKind;
import cz.entity.Client;
import cz.entity.Company;
import cz.entity.Invoice;
import cz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByUser(User user);

    List<Invoice> findByCompany(Company company);

    Optional<Invoice> findByPublicToken(String publicToken);
    List<Invoice> findByClient(Client client);

    List<Invoice> findByUserEmail(String email);

    long countByUserAndIssueDateBetween(User user, LocalDate from, LocalDate to);

    Optional<Invoice> findByUserAndInvoiceNumber(User user, String invoiceNumber);

    @Query("select invoice from Invoice invoice join fetch invoice.user left join fetch invoice.client where invoice.supplierName is null or invoice.customerName is null")
    List<Invoice> findMissingSupplierSnapshot();

    boolean existsBySourceDocumentAndDocumentKind(Invoice sourceDocument, DocumentKind documentKind);

    List<Invoice> findBySourceDocument(Invoice sourceDocument);

    @Query("""
            select count(invoice) from Invoice invoice
            where invoice.user = :user
              and invoice.issueDate between :from and :to
              and (invoice.documentKind is null or invoice.documentKind in :kinds)
            """)
    long countBillable(User user, LocalDate from, LocalDate to, List<DocumentKind> kinds);
}