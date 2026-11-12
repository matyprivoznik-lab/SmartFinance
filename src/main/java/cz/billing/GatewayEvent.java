package cz.billing;

import java.time.Instant;

public record GatewayEvent(
        String type,
        Long userId,
        String checkoutId,
        String customerId,
        String subscriptionId,
        SubscriptionStatus status,
        Instant periodEnd
) {
}
