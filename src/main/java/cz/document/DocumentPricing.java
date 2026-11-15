package cz.document;

import cz.entity.Invoice;
import cz.entity.InvoiceItem;

import java.time.LocalDate;

public final class DocumentPricing {

    private DocumentPricing() {
    }

    public static void apply(Invoice invoice) {
        VatRegime regime = invoice.getVatRegime() == null ? VatRegime.PAYER : invoice.getVatRegime();
        double net = 0.0;
        double gross = 0.0;
        double vat = 0.0;
        double firstRate = 0.0;
        boolean first = true;
        if (invoice.getItems() != null) {
            for (InvoiceItem item : invoice.getItems()) {
                double quantity = item.getQuantity() == null ? 1.0 : item.getQuantity();
                double unitPrice = item.getUnitPriceGross() == null ? 0.0 : item.getUnitPriceGross();
                double requestedRate = item.getVatRate() == null ? 21.0 : item.getVatRate();
                VatCalculator.Line line = VatCalculator.price(regime, quantity, unitPrice, requestedRate);
                item.setInvoice(invoice);
                item.setQuantity(quantity);
                item.setVatRate(line.rate());
                item.setNetAmount(line.net());
                item.setLineGrossTotal(line.gross());
                net += line.net();
                gross += line.gross();
                vat += line.vat();
                if (first) {
                    firstRate = line.rate();
                    first = false;
                }
            }
        }
        invoice.setNetAmount(VatCalculator.round(net));
        invoice.setGrossTotal(VatCalculator.round(gross));
        invoice.setVatAmount(VatCalculator.round(vat));
        invoice.setVatRate((int) Math.round(firstRate));
        if ("CZK".equalsIgnoreCase(invoice.getCurrency())) {
            invoice.setCurrency("CZK");
            invoice.setExchangeRate(1.0);
            invoice.setExchangeRateDate(invoice.getTaxableSupplyDate() != null ? invoice.getTaxableSupplyDate() : invoice.getIssueDate());
            invoice.setCzkNetAmount(invoice.getNetAmount());
            invoice.setCzkVatAmount(invoice.getVatAmount());
            invoice.setCzkGrossTotal(invoice.getGrossTotal());
        }
    }

    public static void applyRate(Invoice invoice, double czkPerUnit, LocalDate date) {
        invoice.setExchangeRate(czkPerUnit);
        invoice.setExchangeRateDate(date);
        invoice.setCzkNetAmount(VatCalculator.round(nullToZero(invoice.getNetAmount()) * czkPerUnit));
        invoice.setCzkVatAmount(VatCalculator.round(nullToZero(invoice.getVatAmount()) * czkPerUnit));
        invoice.setCzkGrossTotal(VatCalculator.round(nullToZero(invoice.getGrossTotal()) * czkPerUnit));
    }

    private static double nullToZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
