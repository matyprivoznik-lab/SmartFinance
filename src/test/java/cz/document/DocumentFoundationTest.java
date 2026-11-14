package cz.document;

import cz.service.CnbExchangeRateService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentFoundationTest {

    @Test
    void numbersFollowTheChosenPatternAndKeepASeparateSequencePerKind() {
        LocalDate date = LocalDate.of(2026, 11, 14);
        assertEquals("2026-001", DocumentNumbers.next("RRRR-CCC", DocumentKind.INVOICE, date, List.of()));
        assertEquals("2026-002", DocumentNumbers.next("RRRR-CCC", DocumentKind.INVOICE, date, List.of("2026-001")));
        assertEquals("FV20260001", DocumentNumbers.next("FVRRRRCCCC", DocumentKind.INVOICE, date, List.of()));
        assertEquals("Z202611-001", DocumentNumbers.next("DRUHRRRRMM-CCC", DocumentKind.PROFORMA, date, List.of("202611-001")));
        assertEquals("N202611-001", DocumentNumbers.next(null, DocumentKind.QUOTE, date, List.of()));
        assertFalse(DocumentNumbers.isValid("bez-poradi"));
        assertTrue(DocumentNumbers.isValid("RRRR-CCC"));
    }

    @Test
    void payerExtractsVatAndReverseChargeKeepsTheWholeAmount() {
        VatCalculator.Line payer = VatCalculator.price(VatRegime.PAYER, 1, 12100, 21);
        assertEquals(10000.0, payer.net(), 0.001);
        assertEquals(2100.0, payer.vat(), 0.001);
        assertEquals(12100.0, payer.gross(), 0.001);

        VatCalculator.Line reverse = VatCalculator.price(VatRegime.REVERSE_CHARGE, 2, 500, 21);
        assertEquals(1000.0, reverse.gross(), 0.001);
        assertEquals(0.0, reverse.vat(), 0.001);
        assertEquals(0.0, reverse.rate(), 0.001);

        VatCalculator.Line reduced = VatCalculator.price(VatRegime.PAYER, 1, 112, 12);
        assertEquals(100.0, reduced.net(), 0.001);
        assertEquals(12.0, reduced.vat(), 0.001);
    }

    @Test
    void cnbLineBecomesCzkPerOneUnit() {
        String body = """
                14.11.2026 #220
                země|měna|množství|kód|kurz
                EMU|euro|1|EUR|24,355
                USA|dolar|1|USD|22,180
                Velká Británie|libra|1|GBP|29,470
                Japonsko|jen|100|JPY|15,200
                """;
        CnbExchangeRateService.Rate eur = CnbExchangeRateService.parse(body, "eur", LocalDate.of(2026, 11, 14)).orElseThrow();
        assertEquals(24.355, eur.czkPerUnit(), 0.0001);
        CnbExchangeRateService.Rate jpy = CnbExchangeRateService.parse(body, "JPY", LocalDate.of(2026, 11, 14)).orElseThrow();
        assertEquals(0.152, jpy.czkPerUnit(), 0.0001);
    }
}
