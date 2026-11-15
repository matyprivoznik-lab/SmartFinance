package cz.billing;

public enum SubscriptionPlan {
    FREE("Freemium", 0, "zdarma"),
    MONTHLY("Premium", 199, "měsíc"),
    YEARLY("Premium", 1990, "rok");

    private final String label;
    private final int priceCzk;
    private final String periodLabel;

    SubscriptionPlan(String label, int priceCzk, String periodLabel) {
        this.label = label;
        this.priceCzk = priceCzk;
        this.periodLabel = periodLabel;
    }

    public String getLabel() {
        return label;
    }

    public int getPriceCzk() {
        return priceCzk;
    }

    public String getPeriodLabel() {
        return periodLabel;
    }

    public boolean isPaid() {
        return this != FREE;
    }
}
