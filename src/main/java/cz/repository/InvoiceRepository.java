package cz.repository;

import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByUser(User user);
    List<Invoice> findByClient(Client client);

    List<Invoice> findByUserEmail(String email);

    long countByUserAndIssueDateBetween(User user, LocalDate from, LocalDate to);

    Optional<Invoice> findByUserAndInvoiceNumber(User user, String invoiceNumber);
}