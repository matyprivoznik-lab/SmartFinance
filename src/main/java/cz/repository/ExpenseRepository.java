package cz.repository;

import cz.entity.Company;
import cz.entity.Expense;
import cz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByCompanyOrderByIssueDateDesc(Company company);

    List<Expense> findByUser(User user);
}
