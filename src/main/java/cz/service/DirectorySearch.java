package cz.service;

import cz.document.DocumentKind;
import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.Product;

import java.text.Normalizer;
import java.util.Locale;

public final class DirectorySearch {

    private DirectorySearch() {
    }

    public static boolean invoiceVisible(Invoice invoice, String query, String kind, String status) {
        if (invoice == null || !matchesQuery(query, invoiceText(invoice))) {
            return false;
        }
        DocumentKind wantedKind = enumOrNull(DocumentKind.class, kind);
        if (wantedKind != null && invoice.getDocumentKind() != wantedKind) {
            return false;
        }
        Invoice.InvoiceStatus wantedStatus = enumOrNull(Invoice.InvoiceStatus.class, status);
        if (wantedStatus != null && statusOf(invoice) != wantedStatus) {
            return false;
        }
        return true;
    }

    public static boolean clientVisible(Client client, String query, String ico) {
        if (client == null || !matchesQuery(query, clientText(client))) {
            return false;
        }
        if ("with".equals(ico) && !hasIco(client)) {
            return false;
        }
        return !"without".equals(ico) || !hasIco(client);
    }

    public static boolean productVisible(Product product, String query, String unit) {
        if (product == null || !matchesQuery(query, productText(product))) {
            return false;
        }
        if (unit == null || unit.isBlank()) {
            return true;
        }
        return unitKey(product).equals(unit.trim());
    }

    public static String invoiceText(Invoice invoice) {
        StringBuilder text = new StringBuilder();
        append(text, invoice.getInvoiceNumber());
        append(text, invoice.getVariableSymbol());
        append(text, invoice.getCustomerName());
        append(text, invoice.getCustomerIco());
        append(text, invoice.getCustomerVatNumber());
        append(text, invoice.getCustomerAddress());
        append(text, invoice.getDocumentKind().getLabel());
        append(text, statusLabel(invoice.getStatus()));
        Client client = invoice.getClient();
        if (client != null) {
            append(text, client.getName());
            append(text, client.getIco());
            append(text, client.getVatNumber());
            append(text, client.getEmail());
            append(text, client.getPhone());
        }
        return text.toString();
    }

    public static String clientText(Client client) {
        StringBuilder text = new StringBuilder();
        append(text, client.getName());
        append(text, client.getIco());
        append(text, client.getVatNumber());
        append(text, client.getAddress());
        append(text, client.getEmail());
        append(text, client.getPhone());
        return text.toString();
    }

    public static String productText(Product product) {
        StringBuilder text = new StringBuilder();
        append(text, product.getName());
        append(text, product.getUnit());
        if (product.getUnitPriceGross() != null) {
            append(text, product.getUnitPriceGross().toPlainString());
        }
        return text.toString();
    }

    public static boolean hasIco(Client client) {
        return client.getIco() != null && !client.getIco().isBlank();
    }

    public static String unitKey(Product product) {
        if (product.getUnit() == null || product.getUnit().isBlank()) {
            return "";
        }
        return product.getUnit().trim();
    }

    public static String statusLabel(Invoice.InvoiceStatus status) {
        return switch (statusOf(status)) {
            case PAID -> "Uhrazeno";
            case OVERDUE -> "Po splatnosti";
            case CANCELLED -> "Storno";
            case ISSUED -> "Vystaveno";
        };
    }

    public static Invoice.InvoiceStatus statusOf(Invoice invoice) {
        return statusOf(invoice.getStatus());
    }

    public static String clean(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }

    static boolean matchesQuery(String query, String haystack) {
        String foldedQuery = fold(query);
        if (foldedQuery.isEmpty()) {
            return true;
        }
        String foldedHay = fold(haystack);
        String compactHay = foldedHay.replace(" ", "");
        for (String part : foldedQuery.split(" ")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!foldedHay.contains(part) && !compactHay.contains(part)) {
                return false;
            }
        }
        return true;
    }

    private static Invoice.InvoiceStatus statusOf(Invoice.InvoiceStatus status) {
        return status == null ? Invoice.InvoiceStatus.ISSUED : status;
    }

    private static void append(StringBuilder text, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!text.isEmpty()) {
            text.append(' ');
        }
        text.append(value.trim());
    }

    private static String fold(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
