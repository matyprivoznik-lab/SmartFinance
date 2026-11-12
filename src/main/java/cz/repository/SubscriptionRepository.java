package cz.repository;

import cz.entity.Subscription;
import cz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUser(User user);

    Optional<Subscription> findByGatewayCheckoutId(String gatewayCheckoutId);

    Optional<Subscription> findByGatewaySubscriptionId(String gatewaySubscriptionId);
}
