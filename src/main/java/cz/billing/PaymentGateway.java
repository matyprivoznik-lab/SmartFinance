package cz.billing;

import cz.entity.User;

import java.util.Optional;

public interface PaymentGateway {

    boolean isConfigured();

    String providerName();

    CheckoutSession createCheckout(User user, SubscriptionPlan plan, String successUrl, String cancelUrl);

    Optional<GatewayEvent> parseWebhook(String payload, String signatureHeader);
}
