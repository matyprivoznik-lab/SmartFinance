package cz.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import cz.entity.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
public class StripePaymentGateway implements PaymentGateway {

    private final BillingProperties properties;

    public StripePaymentGateway(BillingProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return properties.getSecretKey() != null && !properties.getSecretKey().isBlank();
    }

    @Override
    public String providerName() {
        return "Stripe";
    }

    @Override
    public CheckoutSession createCheckout(User user, SubscriptionPlan plan, String successUrl, String cancelUrl) {
        if (!properties.isReadyFor(plan)) {
            throw new IllegalStateException("Stripe není nakonfigurovaný pro plán " + plan.name()
                    + ". Doplň billing.stripe.secret-key a price ID.");
        }

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setClientReferenceId(String.valueOf(user.getId()))
                    .setCustomerEmail(user.getEmail())
                    .putMetadata("userId", String.valueOf(user.getId()))
                    .putMetadata("plan", plan.name())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(properties.priceIdFor(plan))
                            .setQuantity(1L)
                            .build())
                    .build();

            Session session = Session.create(params, requestOptions());
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe Checkout selhal: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<GatewayEvent> parseWebhook(String payload, String signatureHeader) {
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) {
            return Optional.empty();
        }
        try {
            Event event = Webhook.constructEvent(payload, signatureHeader, properties.getWebhookSecret());
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            StripeObject stripeObject = deserializer.getObject().orElse(null);
            if (stripeObject == null) {
                return Optional.empty();
            }

            return switch (event.getType()) {
                case "checkout.session.completed" -> parseCheckoutCompleted((Session) stripeObject);
                case "customer.subscription.updated", "customer.subscription.deleted" ->
                        parseSubscriptionEvent(event.getType(), (Subscription) stripeObject);
                default -> Optional.empty();
            };
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Neplatný Stripe webhook podpis", e);
        }
    }

    private RequestOptions requestOptions() {
        return RequestOptions.builder()
                .setApiKey(properties.getSecretKey())
                .build();
    }

    private Optional<GatewayEvent> parseCheckoutCompleted(Session session) {
        Map<String, String> metadata = session.getMetadata();
        Long userId = null;
        if (metadata != null && metadata.get("userId") != null) {
            userId = Long.valueOf(metadata.get("userId"));
        } else if (session.getClientReferenceId() != null) {
            userId = Long.valueOf(session.getClientReferenceId());
        }

        return Optional.of(new GatewayEvent(
                "checkout.session.completed",
                userId,
                session.getId(),
                session.getCustomer(),
                session.getSubscription(),
                SubscriptionStatus.ACTIVE,
                null
        ));
    }

    private Optional<GatewayEvent> parseSubscriptionEvent(String type, Subscription subscription) {
        Instant periodEnd = subscription.getCurrentPeriodEnd() != null
                ? Instant.ofEpochSecond(subscription.getCurrentPeriodEnd())
                : null;

        SubscriptionStatus status = switch (subscription.getStatus()) {
            case "active", "trialing" -> SubscriptionStatus.ACTIVE;
            case "past_due", "unpaid" -> SubscriptionStatus.PAST_DUE;
            case "canceled", "incomplete_expired" -> SubscriptionStatus.CANCELED;
            default -> SubscriptionStatus.INACTIVE;
        };

        if ("customer.subscription.deleted".equals(type)) {
            status = SubscriptionStatus.CANCELED;
        }

        Long userId = null;
        if (subscription.getMetadata() != null && subscription.getMetadata().get("userId") != null) {
            userId = Long.valueOf(subscription.getMetadata().get("userId"));
        }

        return Optional.of(new GatewayEvent(
                type,
                userId,
                null,
                subscription.getCustomer(),
                subscription.getId(),
                status,
                periodEnd
        ));
    }
}
