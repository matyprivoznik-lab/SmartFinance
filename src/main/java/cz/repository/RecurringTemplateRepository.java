package cz.repository;

import cz.entity.Company;
import cz.entity.RecurringTemplate;
import cz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RecurringTemplateRepository extends JpaRepository<RecurringTemplate, Long> {
    List<RecurringTemplate> findByUserOrderByNextIssueDateAsc(User user);

    List<RecurringTemplate> findByCompanyOrderByNextIssueDateAsc(Company company);

    List<RecurringTemplate> findByActiveTrueAndNextIssueDateLessThanEqual(LocalDate date);
}
