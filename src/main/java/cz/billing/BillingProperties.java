package cz.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.stripe")
public class BillingProperties {

    private String publishableKey = "";
    private String secretKey = "";
    private String webhookSecret = "";
    private String monthlyPriceId = "";
    private String yearlyPriceId = "";

    public String getPublishableKey() {
        return publishableKey;
    }

    public void setPublishableKey(String publishableKey) {
        this.publishableKey = publishableKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getMonthlyPriceId() {
        return monthlyPriceId;
    }

    public void setMonthlyPriceId(String monthlyPriceId) {
        this.monthlyPriceId = monthlyPriceId;
    }

    public String getYearlyPriceId() {
        return yearlyPriceId;
    }

    public void setYearlyPriceId(String yearlyPriceId) {
        this.yearlyPriceId = yearlyPriceId;
    }

    public boolean isReadyFor(SubscriptionPlan plan) {
        if (secretKey == null || secretKey.isBlank()) {
            return false;
        }
        String priceId = priceIdFor(plan);
        return priceId != null && !priceId.isBlank();
    }

    public String priceIdFor(SubscriptionPlan plan) {
        if (plan == SubscriptionPlan.MONTHLY) {
            return monthlyPriceId;
        }
        if (plan == SubscriptionPlan.YEARLY) {
            return yearlyPriceId;
        }
        return "";
    }
}
