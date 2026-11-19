package cz.document;

public enum DocumentKind {
    INVOICE(
            "Běžná faktura",
            "Základní daňový doklad po dodání zboží nebo poskytnutí služby. Zároveň slouží jako výzva k úhradě.",
            "Plnohodnotný daňový doklad. U plátce DPH obsahuje rozpis daně.",
            ""),
    PROFORMA(
            "Zálohová faktura",
            "Výzva k platbě předem, ještě před dodáním zboží nebo služby.",
            "Není účetní ani daňový doklad a nevstupuje do DPH.",
            "Z"),
    ADVANCE_TAX(
            "Daňový doklad k přijaté platbě",
            "Vystavuje ho plátce DPH do 15 dnů od dne, kdy přišly peníze na zálohovou fakturu.",
            "Plnohodnotný daňový doklad. DPH se odvádí ze zálohy.",
            "P"),
    SETTLEMENT(
            "Vyúčtovací faktura",
            "Konečná faktura po dokončení zakázky. Ukáže celou cenu a odečte už zaplacené zálohy.",
            "Daňový doklad. Do DPH vstupuje jen zbývající rozdíl.",
            "V"),
    SUMMARY(
            "Souhrnný daňový doklad",
            "Jeden doklad za víc samostatných plnění pro stejného odběratele v jednom kalendářním měsíci.",
            "Plnohodnotný daňový doklad. Vystavuje se nejpozději do 15 dnů od konce měsíce.",
            "S"),
    CREDIT_NOTE(
            "Dobropis",
            "Snižuje cenu původní faktury, třeba při reklamaci, vrácení nebo dodatečné slevě.",
            "Snižuje základ daně a DPH oproti původní faktuře.",
            "D"),
    DEBIT_NOTE(
            "Vrubopis",
            "Zvyšuje cenu původní faktury, třeba když byla cena účtovaná omylem nízko.",
            "Zvyšuje základ daně a DPH oproti původní faktuře.",
            "B"),
    STORNO(
            "Storno faktury",
            "Úplně ruší původní fakturu od samého začátku, třeba když byla vystavená omylem.",
            "Původní doklad se zneplatní a jeho hodnota se bere jako nula.",
            "X"),
    QUOTE(
            "Cenová nabídka",
            "Návrh ceny před objednávkou.",
            "Není daňový doklad.",
            "N"),
    ORDER(
            "Objednávka",
            "Potvrzení objednávky před dodáním.",
            "Není daňový doklad.",
            "O");

    private final String label;
    private final String description;
    private final String taxImpact;
    private final String seriesCode;

    DocumentKind(String label, String description, String taxImpact, String seriesCode) {
        this.label = label;
        this.description = description;
        this.taxImpact = taxImpact;
        this.seriesCode = seriesCode;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getTaxImpact() {
        return taxImpact;
    }

    public String getSeriesCode() {
        return seriesCode;
    }

    public boolean isTaxDocument() {
        return this != PROFORMA && this != QUOTE && this != ORDER;
    }

    public boolean countsTowardQuota() {
        return this != QUOTE && this != ORDER;
    }

    public boolean canBecomeInvoice() {
        return this == QUOTE || this == ORDER;
    }

    public boolean issuedOnItsOwn() {
        return this == INVOICE || this == PROFORMA || this == QUOTE || this == ORDER;
    }

    public boolean requestsPayment() {
        return this == INVOICE || this == PROFORMA || this == SETTLEMENT || this == SUMMARY || this == DEBIT_NOTE;
    }
}
