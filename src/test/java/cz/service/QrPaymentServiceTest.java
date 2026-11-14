package cz.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import cz.entity.Invoice;
import cz.entity.User;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrPaymentServiceTest {

    private final QrPaymentService service = new QrPaymentService();

    @Test
    void convertsCzechAccountAndExistingIban() {
        assertEquals("CZ6508000000192000145399", QrPaymentService.czechAccountToIban("19-2000145399/0800"));
        assertEquals("CZ1801000000000123456789", QrPaymentService.czechAccountToIban("123456789/0100"));
        assertEquals("CZ6508000000192000145399", QrPaymentService.czechAccountToIban("CZ65 0800 0000 1920 0014 5399"));
        assertNull(QrPaymentService.czechAccountToIban("not-an-account"));
        assertNull(QrPaymentService.czechAccountToIban("CZ0008000000192000145399"));
    }

    @Test
    void buildsSpdFromDomesticAccountAndDecodesTheQr() throws Exception {
        User user = new User();
        user.setName("Jan Novák (Test)");
        user.setBankAccount("123456789/0100");
        user.setSwift("KOMB CZ PP");

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("2026001");
        invoice.setVariableSymbol("2026001");
        invoice.setConstantSymbol("0308");
        invoice.setGrossTotal(12100.0);
        invoice.setDueDate(LocalDate.of(2026, 3, 1));
        invoice.setUser(user);

        String expected = "SPD*1.0*ACC:CZ1801000000000123456789+KOMBCZPP*AM:12100.00*CC:CZK*DT:20260301*RN:Jan Novak (Test)*MSG:Faktura 2026001*X-VS:2026001*X-KS:0308*";
        assertEquals(expected, service.spdPayload(user, invoice));

        invoice.captureSupplier(user);
        user.setName("Po změně v ARES");
        assertEquals(expected, service.spdPayload(user, invoice));

        invoice.setIssueDate(LocalDate.of(2026, 2, 15));
        invoice.setTaxableSupplyDate(LocalDate.of(2026, 2, 15));
        invoice.setSupplierIco("12345678");
        invoice.setSupplierVatNumber("CZ12345678");
        invoice.setCustomerIco("87654321");
        invoice.setCustomerVatNumber("CZ87654321");
        invoice.setNetAmount(10000.0);
        invoice.setVatAmount(2100.0);
        invoice.setVatRate(21);
        String withInvoice = service.spdPayload(user, invoice);
        assertTrue(withInvoice.contains("X-INV:SID%2A1.0%2A"));
        assertTrue(withInvoice.contains("DD:20260215"));
        assertTrue(withInvoice.contains("INI:12345678"));
        assertTrue(withInvoice.contains("VII:CZ12345678"));
        assertTrue(withInvoice.contains("INR:87654321"));
        assertTrue(withInvoice.contains("VIR:CZ87654321"));
        assertTrue(withInvoice.contains("TB0:10000.00"));
        assertTrue(withInvoice.contains("T0:2100.00"));

        String dataUrl = service.qrCodeDataUrl(user, invoice);
        assertTrue(dataUrl.startsWith("data:image/png;base64,"));
        byte[] png = Base64.getDecoder().decode(dataUrl.substring("data:image/png;base64,".length()));
        var image = ImageIO.read(new ByteArrayInputStream(png));
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        assertEquals(withInvoice, new MultiFormatReader().decode(bitmap).getText());
    }

    @Test
    void skipsQrWhenTheAccountIsMissing() {
        User user = new User();
        user.setName("Bez uctu");
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("2026002");
        invoice.setGrossTotal(100.0);

        assertNull(service.paymentIban(user));
        assertNull(service.spdPayload(user, invoice));
        assertNull(service.qrCodeDataUrl(user, invoice));
    }
}
