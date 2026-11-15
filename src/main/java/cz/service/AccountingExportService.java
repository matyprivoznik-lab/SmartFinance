package cz.service;

import cz.entity.Company;
import cz.entity.Expense;
import cz.entity.Invoice;
import cz.repository.ExpenseRepository;
import cz.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class AccountingExportService {

    private final InvoiceRepository invoiceRepository;
    private final ExpenseRepository expenseRepository;

    public AccountingExportService(InvoiceRepository invoiceRepository, ExpenseRepository expenseRepository) {
        this.invoiceRepository = invoiceRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional(readOnly = true)
    public byte[] csv(Company company) {
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("smer;typ;cislo;datum;splatnost;odberatel;ico;mena;zaklad;dph;celkem;stav\n");
        for (Invoice invoice : invoiceRepository.findByCompany(company)) {
            csv.append(row("vydany",
                    invoice.getDocumentKind().name(),
                    invoice.getInvoiceNumber(),
                    text(invoice.getIssueDate()),
                    text(invoice.getDueDate()),
                    invoice.getCustomerName(),
                    invoice.getCustomerIco(),
                    invoice.getCurrency(),
                    invoice.getNetAmount(),
                    invoice.getVatAmount(),
                    invoice.getGrossTotal(),
                    invoice.getStatus().name()));
        }
        for (Expense expense : expenseRepository.findByCompanyOrderByIssueDateDesc(company)) {
            csv.append(row("prijaty",
                    "NAKLAD",
                    expense.getDocumentNumber(),
                    text(expense.getIssueDate()),
                    "",
                    expense.getSupplierName(),
                    expense.getSupplierIco(),
                    "CZK",
                    expense.getNetAmount(),
                    expense.getVatAmount(),
                    expense.getGrossTotal(),
                    ""));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] pohoda(Company company) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<dat:dataPack xmlns:dat=\"http://www.stormware.cz/schema/version_2/data.xsd\" ");
        xml.append("xmlns:inv=\"http://www.stormware.cz/schema/version_2/invoice.xsd\" ");
        xml.append("xmlns:typ=\"http://www.stormware.cz/schema/version_2/type.xsd\" ");
        xml.append("id=\"mujdokladomat\" ico=\"").append(escape(company.getIco())).append("\" application=\"mujDokladomat\" version=\"2.0\">");
        for (Invoice invoice : invoiceRepository.findByCompany(company)) {
            xml.append("<dat:dataPackItem id=\"").append(escape(invoice.getInvoiceNumber())).append("\" version=\"2.0\">");
            xml.append("<inv:invoice version=\"2.0\"><inv:invoiceHeader>");
            xml.append("<inv:invoiceType>issuedInvoice</inv:invoiceType>");
            xml.append("<inv:number><typ:numberRequested>").append(escape(invoice.getInvoiceNumber())).append("</typ:numberRequested></inv:number>");
            xml.append("<inv:symVar>").append(escape(invoice.getVariableSymbol())).append("</inv:symVar>");
            xml.append("<inv:date>").append(text(invoice.getIssueDate())).append("</inv:date>");
            xml.append("<inv:dateDue>").append(text(invoice.getDueDate())).append("</inv:dateDue>");
            xml.append("<inv:dateTax>").append(text(invoice.getTaxableSupplyDate())).append("</inv:dateTax>");
            xml.append("<inv:partnerIdentity><typ:address>");
            xml.append("<typ:company>").append(escape(invoice.getCustomerName())).append("</typ:company>");
            xml.append("<typ:ico>").append(escape(invoice.getCustomerIco())).append("</typ:ico>");
            xml.append("</typ:address></inv:partnerIdentity>");
            xml.append("<inv:myIdentity><typ:address>");
            xml.append("<typ:company>").append(escape(invoice.getSupplierName())).append("</typ:company>");
            xml.append("<typ:ico>").append(escape(invoice.getSupplierIco())).append("</typ:ico>");
            xml.append("</typ:address></inv:myIdentity>");
            xml.append("</inv:invoiceHeader><inv:invoiceSummary>");
            xml.append("<inv:homeCurrency><typ:priceNone>").append(money(invoice.getNetAmount())).append("</typ:priceNone>");
            xml.append("<typ:priceSum>").append(money(invoice.getGrossTotal())).append("</typ:priceSum></inv:homeCurrency>");
            xml.append("</inv:invoiceSummary></inv:invoice></dat:dataPackItem>");
        }
        xml.append("</dat:dataPack>");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String row(String direction, String type, String number, String date, String due, String name,
                              String ico, String currency, Double net, Double vat, Double gross, String status) {
        return String.join(";",
                csv(direction), csv(type), csv(number), csv(date), csv(due), csv(name), csv(ico), csv(currency),
                money(net), money(vat), money(gross), csv(status)) + "\n";
    }

    private static String csv(String value) {
        String text = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String money(Double value) {
        return value == null ? "0.00" : String.format(java.util.Locale.US, "%.2f", value);
    }
}
