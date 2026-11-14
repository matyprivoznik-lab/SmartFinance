package cz.document;

public final class VatCalculator {

    private VatCalculator() {
    }

    public static Line price(VatRegime regime, double quantity, double unitPrice, double vatRate) {
        double gross = round(quantity * unitPrice);
        if (regime == null || !regime.chargesCzechVat()) {
            return new Line(gross, gross, 0.0, 0.0);
        }
        double rate = normalizeRate(vatRate);
        if (rate <= 0.0) {
            return new Line(gross, gross, 0.0, 0.0);
        }
        double net = round(gross / (1.0 + (rate / 100.0)));
        return new Line(net, gross, round(gross - net), rate);
    }

    public static double normalizeRate(double vatRate) {
        if (Math.abs(vatRate - 12.0) < 0.001) {
            return 12.0;
        }
        if (vatRate <= 0.0) {
            return 0.0;
        }
        return 21.0;
    }

    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record Line(double net, double gross, double vat, double rate) {
    }
}
