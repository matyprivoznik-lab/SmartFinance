package cz.repository;

import cz.entity.BankConnection;
import cz.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankConnectionRepository extends JpaRepository<BankConnection, Long> {

    List<BankConnection> findByCompanyOrderByIdAsc(Company company);

    Optional<BankConnection> findByReference(String reference);

    List<BankConnection> findByStatus(String status);
}
