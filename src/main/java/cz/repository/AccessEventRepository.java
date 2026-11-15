package cz.repository;

import cz.entity.AccessEvent;
import cz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AccessEventRepository extends JpaRepository<AccessEvent, Long> {

    List<AccessEvent> findTop200ByOrderByCreatedAtDesc();

    @Modifying
    @Query("update AccessEvent event set event.user = null where event.user = :user")
    void detachUser(User user);
}
