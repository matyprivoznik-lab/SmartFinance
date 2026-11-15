package cz.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankStatementTest {

    @Test
    void readsVariableSymbolFromGpcCamtAndOpenBanking() {
        String gpc = gpcLine();
        List<PaymentMatchService.BankRow> gpcRows = BankStatementParser.parse(gpc.getBytes(StandardCharsets.UTF_8), "vypis.gpc");
        assertEquals(1, gpcRows.size());
        assertEquals("2026001", gpcRows.get(0).variableSymbol().replaceFirst("^0+", ""));
        assertEquals(1500.0, gpcRows.get(0).amount());
        assertEquals(LocalDate.of(2026, 11, 14), gpcRows.get(0).date());

        String camt = """
                <Document><BkToCstmrStmt><Stmt><Ntry>
                  <Amt Ccy="CZK">1500.00</Amt>
                  <CdtDbtInd>CRDT</CdtDbtInd>
                  <BookgDt><Dt>2026-11-14</Dt></BookgDt>
                  <NtryDtls><TxDtls><RmtInf><Ustrd>Faktura VS:2026001</Ustrd></RmtInf></TxDtls></NtryDtls>
                </Ntry><Ntry>
                  <Amt Ccy="CZK">20.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <BookgDt><Dt>2026-11-14</Dt></BookgDt>
                </Ntry></Stmt></BkToCstmrStmt></Document>
                """;
        List<PaymentMatchService.BankRow> camtRows = BankStatementParser.parseCamt(camt);
        assertEquals(1, camtRows.size());
        assertEquals("2026001", camtRows.get(0).variableSymbol());

        String banking = """
                {"transactions":{"booked":[
                  {"transactionAmount":{"currency":"CZK","amount":"1500.00"},"bookingDate":"2026-11-14","remittanceInformationUnstructured":"platba /VS2026001/SS/KS"},
                  {"transactionAmount":{"currency":"CZK","amount":"-40.00"},"bookingDate":"2026-11-14","remittanceInformationUnstructured":"VS:999"}
                ],"pending":[
                  {"transactionAmount":{"currency":"CZK","amount":"1500.00"},"remittanceInformationUnstructured":"VS:2026001"}
                ]}}
                """;
        List<PaymentMatchService.BankRow> booked = OpenBankingService.parseBooked(banking);
        assertEquals(1, booked.size());
        assertEquals(1500.0, booked.get(0).amount());
        assertTrue(OpenBankingService.catalog().stream().anyMatch(bank -> bank.name().contains("Air Bank")));
    }

    private static String gpcLine() {
        char[] line = new char[128];
        java.util.Arrays.fill(line, ' ');
        put(line, 0, "075");
        put(line, 48, "000000150000");
        put(line, 60, "2");
        put(line, 61, "0002026001");
        put(line, 91, "141126");
        return new String(line);
    }

    private static void put(char[] line, int at, String value) {
        for (int i = 0; i < value.length(); i++) {
            line[at + i] = value.charAt(i);
        }
    }
}
