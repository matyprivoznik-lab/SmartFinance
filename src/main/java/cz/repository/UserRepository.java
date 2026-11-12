package cz.repository;

import cz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Přidáno pro podporu ověření IČO v AuthControlleru
    Optional<User> findByIco(String ico);

    boolean existsByIco(String ico);
}