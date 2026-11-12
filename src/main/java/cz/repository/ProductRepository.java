package cz.repository;

import cz.entity.Product;
import cz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByUser(User user);
    long countByUser(User user);
    List<Product> findByUserEmail(String email);
    long countByUserEmail(String email);
}