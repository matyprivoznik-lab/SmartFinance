package cz.repository;

import cz.entity.Client;
import cz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    List<Client> findByUser(User user);
    List<Client> findByUserEmail(String email);
}