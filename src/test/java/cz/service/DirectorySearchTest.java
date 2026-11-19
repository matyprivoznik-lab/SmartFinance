package cz.service;

import cz.document.DocumentKind;
import cz.entity.Client;
import cz.entity.Invoice;
import cz.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectorySearchTest {

    @Test
    void findsInvoiceByCustomerNumberEvenWhenSpacesDiffer() {
        Invoice invoice = invoice("2026-001", "87654321", "ACME Corp s.r.o.");
        invoice.setVariableSymbol("2026001");
        invoice.setCustomerVatNumber("CZ87654321");

        assertTrue(DirectorySearch.invoiceVisible(invoice, "876 543 21", "", ""));
        assertTrue(DirectorySearch.invoiceVisible(invoice, "acme 2026", "", ""));
        assertTrue(DirectorySearch.invoiceVisible(invoice, "cz87654321", "", ""));
        assertFalse(DirectorySearch.invoiceVisible(invoice, "jina firma", "", ""));
    }

    @Test
    void usesTheLinkedContactWhenTheInvoiceHasNoCustomerSnapshot() {
        Client client = new Client();
        client.setName("Modrá Sova");
        client.setIco("27074358");
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("2026-002");
        invoice.setClient(client);

        assertTrue(DirectorySearch.invoiceVisible(invoice, "27074358", "", ""));
        assertTrue(DirectorySearch.invoiceVisible(invoice, "modra", "", ""));
    }

    @Test
    void filtersInvoicesByKindAndStatus() {
        Invoice invoice = invoice("2026-003", "12345678", "Firma");
        invoice.setDocumentKind(DocumentKind.PROFORMA);
        invoice.setStatus(Invoice.InvoiceStatus.PAID);

        assertTrue(DirectorySearch.invoiceVisible(invoice, "", "PROFORMA", "PAID"));
        assertTrue(DirectorySearch.invoiceVisible(invoice, "uhrazeno", "", ""));
        assertFalse(DirectorySearch.invoiceVisible(invoice, "", "INVOICE", ""));
        assertFalse(DirectorySearch.invoiceVisible(invoice, "", "", "OVERDUE"));
        assertTrue(DirectorySearch.invoiceVisible(invoice, "", "nema-druh", "PAID"));
    }

    @Test
    void findsContactsByNumberAndCanRequireIco() {
        Client withIco = new Client();
        withIco.setName("Švec a syn");
        withIco.setIco("123 45 678");
        withIco.setEmail("svec@example.com");
        withIco.setPhone("+420 777 111 222");

        Client withoutIco = new Client();
        withoutIco.setName("Jan Bez IČA");
        withoutIco.setEmail("jan@example.com");

        assertTrue(DirectorySearch.clientVisible(withIco, "12345678", ""));
        assertTrue(DirectorySearch.clientVisible(withIco, "svec", "with"));
        assertTrue(DirectorySearch.clientVisible(withIco, "777111", ""));
        assertFalse(DirectorySearch.clientVisible(withIco, "svec", "without"));
        assertTrue(DirectorySearch.clientVisible(withoutIco, "jan", "without"));
        assertFalse(DirectorySearch.clientVisible(withoutIco, "jan", "with"));
    }

    @Test
    void findsProductsByNameUnitAndPrice() {
        Product product = new Product();
        product.setName("Konzultace");
        product.setUnit("hod");
        product.setUnitPriceGross(new BigDecimal("1500.00"));

        assertTrue(DirectorySearch.productVisible(product, "konz", ""));
        assertTrue(DirectorySearch.productVisible(product, "1500", "hod"));
        assertFalse(DirectorySearch.productVisible(product, "konz", "ks"));
        assertFalse(DirectorySearch.productVisible(product, "hosting", ""));
    }

    private static Invoice invoice(String number, String ico, String name) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(number);
        invoice.setCustomerIco(ico);
        invoice.setCustomerName(name);
        return invoice;
    }
}
