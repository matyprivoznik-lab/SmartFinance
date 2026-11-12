package cz.service;

import cz.billing.CheckoutSession;
import cz.billing.GatewayEvent;
import cz.billing.PaymentGateway;
import cz.billing.SubscriptionPlan;
import cz.billing.SubscriptionStatus;
import cz.entity.Subscription;
import cz.entity.User;
import cz.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Service
public class BillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentGateway paymentGateway;

    public BillingService(SubscriptionRepository subscriptionRepository, PaymentGateway paymentGateway) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentGateway = paymentGateway;
    }

    public PaymentGateway getPaymentGateway() {
        return paymentGateway;
    }

    @Transactional
    public Subscription getOrCreateSubscription(User user) {
        return subscriptionRepository.findByUser(user).orElseGet(() -> {
            Subscription subscription = new Subscription();
            subscription.setUser(user);
            subscription.setPlan(SubscriptionPlan.FREE);
            subscription.setStatus(SubscriptionStatus.INACTIVE);
            return subscriptionRepository.save(subscription);
        });
    }

    public boolean isActive(User user) {
        return subscriptionRepository.findByUser(user)
                .map(s -> s.getStatus() == SubscriptionStatus.ACTIVE && s.getPlan() != null && s.getPlan().isPaid())
                .orElse(false);
    }

    @Transactional
    public CheckoutSession startCheckout(User user, SubscriptionPlan plan, String successUrl, String cancelUrl) {
        if (!plan.isPaid()) {
            throw new IllegalArgumentException("Nelze platit za FREE plán.");
        }

        Subscription subscription = getOrCreateSubscription(user);

        if (!paymentGateway.isConfigured()) {
            // Lokální demo bez Stripe klíčů – aktivace ihned přes success URL
            String demoCheckoutId = "demo_" + UUID.randomUUID();
            subscription.setPlan(plan);
            subscription.setStatus(SubscriptionStatus.CHECKOUT);
            subscription.setGatewayCheckoutId(demoCheckoutId);
            subscriptionRepository.save(subscription);

            String redirect = successUrl
                    + (successUrl.contains("?") ? "&" : "?")
                    + "demo=1&plan=" + plan.name()
                    + "&checkout_id=" + demoCheckoutId;
            return new CheckoutSession(demoCheckoutId, redirect);
        }

        CheckoutSession session = paymentGateway.createCheckout(user, plan, successUrl, cancelUrl);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.CHECKOUT);
        subscription.setGatewayCheckoutId(session.id());
        subscriptionRepository.save(subscription);
        return session;
    }

    @Transactional
    public void activateDemoCheckout(User user, SubscriptionPlan plan, String checkoutId) {
        Subscription subscription = getOrCreateSubscription(user);
        if (checkoutId != null && !checkoutId.equals(subscription.getGatewayCheckoutId())) {
            throw new IllegalStateException("Neplatný demo checkout.");
        }
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(plan == SubscriptionPlan.YEARLY ? 12 : 1));
        subscription.setGatewayCheckoutId(checkoutId);
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void cancelSubscription(User user) {
        Subscription subscription = getOrCreateSubscription(user);
        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscription.setPlan(SubscriptionPlan.FREE);
        subscription.setCurrentPeriodEnd(LocalDateTime.now());
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void handleGatewayEvent(GatewayEvent event) {
        Optional<Subscription> found = Optional.empty();

        if (event.checkoutId() != null) {
            found = subscriptionRepository.findByGatewayCheckoutId(event.checkoutId());
        }
        if (found.isEmpty() && event.subscriptionId() != null) {
            found = subscriptionRepository.findByGatewaySubscriptionId(event.subscriptionId());
        }
        if (found.isEmpty() && event.userId() != null) {
            found = subscriptionRepository.findAll().stream()
                    .filter(s -> s.getUser() != null && event.userId().equals(s.getUser().getId()))
                    .findFirst();
        }
        if (found.isEmpty()) {
            return;
        }

        Subscription subscription = found.get();
        if (event.customerId() != null) {
            subscription.setGatewayCustomerId(event.customerId());
        }
        if (event.subscriptionId() != null) {
            subscription.setGatewaySubscriptionId(event.subscriptionId());
        }
        if (event.checkoutId() != null) {
            subscription.setGatewayCheckoutId(event.checkoutId());
        }
        if (event.status() != null) {
            subscription.setStatus(event.status());
            if (event.status() == SubscriptionStatus.CANCELED) {
                subscription.setPlan(SubscriptionPlan.FREE);
            }
        }
        if (event.periodEnd() != null) {
            subscription.setCurrentPeriodEnd(LocalDateTime.ofInstant(event.periodEnd(), ZoneId.systemDefault()));
        } else if (event.status() == SubscriptionStatus.ACTIVE) {
            SubscriptionPlan plan = subscription.getPlan() != null ? subscription.getPlan() : SubscriptionPlan.MONTHLY;
            subscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(plan == SubscriptionPlan.YEARLY ? 12 : 1));
        }

        subscriptionRepository.save(subscription);
    }
}
