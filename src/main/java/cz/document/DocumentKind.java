package cz.document;

public enum DocumentKind {
    INVOICE("Faktura", "Daňový doklad", ""),
    PROFORMA("Zálohová faktura", "Výzva k platbě, není daňový doklad", "Z"),
    CREDIT_NOTE("Opravný daňový doklad", "Dobropis k původní faktuře", "D"),
    QUOTE("Cenová nabídka", "Není daňový doklad", "N"),
    ORDER("Objednávka", "Není daňový doklad", "O");

    private final String label;
    private final String description;
    private final String seriesCode;

    DocumentKind(String label, String description, String seriesCode) {
        this.label = label;
        this.description = description;
        this.seriesCode = seriesCode;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getSeriesCode() {
        return seriesCode;
    }

    public boolean countsTowardQuota() {
        return this == INVOICE || this == PROFORMA || this == CREDIT_NOTE;
    }

    public boolean canBecomeInvoice() {
        return this == QUOTE || this == ORDER || this == PROFORMA;
    }
}
