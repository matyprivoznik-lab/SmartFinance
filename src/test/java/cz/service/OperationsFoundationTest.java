package cz.service;

import cz.document.DocumentKind;
import cz.document.ReminderPolicy;
import cz.entity.Invoice;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationsFoundationTest {

    @Test
    void reminderSendsFormalOnlyWhenBothThresholdsAreAlreadyDue() {
        LocalDate due = LocalDate.of(2026, 11, 1);
        assertEquals(ReminderPolicy.Action.NONE, ReminderPolicy.decide(due, due.plusDays(2), 3, 14, false, false, true));
        assertEquals(ReminderPolicy.Action.GENTLE, ReminderPolicy.decide(due, due.plusDays(3), 3, 14, false, false, true));
        assertEquals(ReminderPolicy.Action.NONE, ReminderPolicy.decide(due, due.plusDays(4), 3, 14, true, false, true));
        assertEquals(ReminderPolicy.Action.FORMAL, ReminderPolicy.decide(due, due.plusDays(20), 3, 14, false, false, true));
        assertEquals(ReminderPolicy.Action.NONE, ReminderPolicy.decide(due, due.plusDays(20), 3, 14, false, true, true));
        assertEquals(ReminderPolicy.Action.NONE, ReminderPolicy.decide(due, due.plusDays(20), 3, 14, false, false, false));
    }

    @Test
    void csvAndFioMatchTheOpenInvoiceBySymbolAndAmount() {
        assertEquals(1, PaymentMatchService.parseCsv("datum;castka;vs\n01.11.2026;1210,50;2026001\n").size());
        PaymentMatchService.BankRow row = PaymentMatchService.parseCsv("2026-11-01;100.00;2026001").get(0);
        Invoice invoice = new Invoice();
        invoice.setId(4L);
        invoice.setInvoiceNumber("2026-001");
        invoice.setVariableSymbol("2026001");
        invoice.setGrossTotal(100.0);
        invoice.setStatus(Invoice.InvoiceStatus.ISSUED);
        invoice.setDocumentKind(DocumentKind.INVOICE);
        assertEquals(invoice, PaymentMatchService.findMatch(List.of(invoice), row));

        Invoice foreign = new Invoice();
        foreign.setId(5L);
        foreign.setVariableSymbol("2026001");
        foreign.setCurrency("EUR");
        foreign.setGrossTotal(10.0);
        foreign.setCzkGrossTotal(250.0);
        foreign.setStatus(Invoice.InvoiceStatus.ISSUED);
        assertNull(PaymentMatchService.findMatch(List.of(foreign), row));
        assertEquals(foreign, PaymentMatchService.findMatch(List.of(foreign), new PaymentMatchService.BankRow(LocalDate.of(2026, 11, 1), 250.0, "2026001")));

        String fio = """
                {"accountStatement":{"transactionList":{"transaction":[
                  {"column0":{"value":"2026-11-02"},"column1":{"value":150.5},"column5":{"value":"9988"}}
                ]}}}
                """;
        List<PaymentMatchService.BankRow> parsed = new PaymentMatchService(null, null).parseFio(fio);
        assertEquals(1, parsed.size());
        assertEquals(150.5, parsed.get(0).amount());
        assertEquals("9988", parsed.get(0).variableSymbol());
    }

    @Test
    void registersStayUnknownWhenTheAnswerIsMissing() {
        assertEquals(RiskCheckService.Insolvency.OK, RiskCheckService.insolvency("Nebyly nalezeny žádné záznamy"));
        assertEquals(RiskCheckService.Insolvency.FOUND, RiskCheckService.insolvency("spisová značka INS 1234/2026"));
        assertEquals(RiskCheckService.Insolvency.UNKNOWN, RiskCheckService.insolvency(""));
        assertTrue(RiskCheckService.listedAsUnreliable("ico;dic\n12345678;CZ12345678", "12345678", null));
        assertFalse(RiskCheckService.listedAsUnreliable("ico;dic\n87654321;CZ87654321", "12345678", "CZ12345678"));
    }
}
