package cz.repository;

import cz.entity.Company;
import cz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    List<Company> findByOwnerOrderByIdAsc(User owner);

    Optional<Company> findByOwnerAndIco(User owner, String ico);
}
