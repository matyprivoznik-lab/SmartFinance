package cz.document;

public enum VatRegime {
    NON_PAYER("Neplátce DPH", "Doklad je bez daně. Částka je zároveň základem i úhradou."),
    PAYER("Plátce DPH", "Sazby 21 %, 12 % a 0 %. Cena položky je včetně DPH."),
    IDENTIFIED_PERSON("Identifikovaná osoba", "Služba do EU. Česká DPH se neúčtuje, daň přiznává odběratel."),
    REVERSE_CHARGE("Přenesená daňová povinnost", "Daň přiznává odběratel. Typicky stavebnictví nebo elektronika.");

    private final String label;
    private final String description;

    VatRegime(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public boolean chargesCzechVat() {
        return this == PAYER;
    }
}
