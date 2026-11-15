package cz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.document.DocumentKind;
import cz.entity.Company;
import cz.entity.Invoice;
import cz.entity.User;
import cz.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PaymentMatchService {

    private static final DateTimeFormatter FIO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private final InvoiceRepository invoiceRepository;
    private final DocumentWorkflowService documentWorkflowService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentMatchService(InvoiceRepository invoiceRepository, DocumentWorkflowService documentWorkflowService) {
        this.invoiceRepository = invoiceRepository;
        this.documentWorkflowService = documentWorkflowService;
    }

    public record BankRow(LocalDate date, double amount, String variableSymbol) {
    }

    public record Result(int matched, int skipped, List<String> notes) {
        public String message() {
            return "Spárováno " + matched + " plateb, přeskočeno " + skipped + ".";
        }
    }

    @Transactional
    public Result importCsv(User user, Company company, String csv) {
        return apply(user, company, parseCsv(csv));
    }

    @Transactional
    public Result importFio(User user, Company company, LocalDate from, LocalDate to) {
        if (company == null || company.getFioApiToken() == null || company.getFioApiToken().isBlank()) {
            throw new IllegalArgumentException("Nejdřív v nastavení firmy ulož token Fio API.");
        }
        String token = company.getFioApiToken().trim();
        String url = "https://fioapi.fio.cz/v1/rest/periods/" + token + "/"
                + from.format(FIO_DATE) + "/" + to.format(FIO_DATE) + "/transactions.json";
        String body;
        try {
            body = new RestTemplate().getForObject(url, String.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Fio API teď neodpovědělo. Výpis můžeš nahrát jako CSV.");
        }
        return apply(user, company, parseFio(body));
    }

    @Transactional
    public Result apply(User user, Company company, List<BankRow> rows) {
        List<Invoice> open = company == null ? List.of() : invoiceRepository.findByCompany(company).stream()
                .filter(invoice -> invoice.getStatus() != Invoice.InvoiceStatus.PAID
                        && invoice.getStatus() != Invoice.InvoiceStatus.CANCELLED)
                .filter(invoice -> invoice.getDocumentKind() != DocumentKind.QUOTE
                        && invoice.getDocumentKind() != DocumentKind.ORDER)
                .toList();
        int matched = 0;
        int skipped = 0;
        List<String> notes = new ArrayList<>();
        for (BankRow row : rows) {
            Invoice invoice = findMatch(open, row);
            if (invoice == null) {
                skipped++;
                notes.add("VS " + row.variableSymbol() + " na " + row.amount() + " Kč nemá doklad.");
                continue;
            }
            invoice.setStatus(Invoice.InvoiceStatus.PAID);
            invoice.setPaidAt(row.date() == null ? LocalDate.now() : row.date());
            invoiceRepository.save(invoice);
            open = open.stream().filter(item -> !item.getId().equals(invoice.getId())).toList();
            matched++;
            if (invoice.getDocumentKind() == DocumentKind.PROFORMA) {
                try {
                    documentWorkflowService.followUp(user, invoice);
                    notes.add("Záloha " + invoice.getInvoiceNumber() + " je uhrazená a vzniklo vyúčtování.");
                } catch (IllegalArgumentException ex) {
                    notes.add("Záloha " + invoice.getInvoiceNumber() + " je uhrazená.");
                }
            }
        }
        return new Result(matched, skipped, notes);
    }

    public static Invoice findMatch(List<Invoice> open, BankRow row) {
        if (row == null || row.variableSymbol() == null) {
            return null;
        }
        String symbol = digits(row.variableSymbol());
        if (symbol.isEmpty()) {
            return null;
        }
        Invoice found = null;
        for (Invoice invoice : open) {
            if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
                continue;
            }
            String invoiceSymbol = digits(invoice.getVariableSymbol());
            String numberSymbol = digits(invoice.getInvoiceNumber());
            if (!symbol.equals(invoiceSymbol) && !symbol.equals(numberSymbol)) {
                continue;
            }
            double expected = payable(invoice);
            if (Math.abs(expected - row.amount()) > 0.5) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = invoice;
        }
        return found;
    }

    public static List<BankRow> parseCsv(String csv) {
        List<BankRow> rows = new ArrayList<>();
        if (csv == null) {
            return rows;
        }
        for (String rawLine : csv.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split("[;,]");
            if (columns.length < 3) {
                continue;
            }
            if (columns[1].toLowerCase(Locale.ROOT).contains("cast") || columns[2].equalsIgnoreCase("vs")) {
                continue;
            }
            LocalDate date = parseDate(columns[0].trim());
            Double amount = parseAmount(columns[1].trim());
            String symbol = digits(columns[2]);
            if (amount == null || symbol.isEmpty()) {
                continue;
            }
            rows.add(new BankRow(date, amount, symbol));
        }
        return rows;
    }

    public List<BankRow> parseFio(String json) {
        List<BankRow> rows = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return rows;
        }
        try {
            JsonNode transactions = objectMapper.readTree(json)
                    .path("accountStatement")
                    .path("transactionList")
                    .path("transaction");
            if (!transactions.isArray()) {
                return rows;
            }
            for (JsonNode transaction : transactions) {
                Double amount = columnAmount(transaction, "column1");
                String symbol = digits(columnText(transaction, "column5"));
                LocalDate date = parseDate(columnText(transaction, "column0"));
                if (amount == null || symbol.isEmpty() || amount <= 0) {
                    continue;
                }
                rows.add(new BankRow(date, amount, symbol));
            }
        } catch (Exception ignored) {
            return rows;
        }
        return rows;
    }

    private static Double columnAmount(JsonNode transaction, String name) {
        JsonNode column = transaction.get(name);
        if (column == null) {
            return null;
        }
        if (column.has("value")) {
            return parseAmount(column.get("value").asText());
        }
        return parseAmount(column.asText());
    }

    private static String columnText(JsonNode transaction, String name) {
        JsonNode column = transaction.get(name);
        if (column == null || column.isNull()) {
            return "";
        }
        if (column.has("value")) {
            return column.get("value").asText("");
        }
        return column.asText("");
    }

    static double payable(Invoice invoice) {
        if (invoice.getCurrency() != null && !"CZK".equalsIgnoreCase(invoice.getCurrency()) && invoice.getCzkGrossTotal() != null) {
            return invoice.getCzkGrossTotal();
        }
        return invoice.getGrossTotal() == null ? 0.0 : invoice.getGrossTotal();
    }

    private static Double parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.replace(" ", "").replace("\u00a0", "").replace("Kč", "").replace("CZK", "");
        if (normalized.chars().filter(ch -> ch == ',').count() == 1 && !normalized.contains(".")) {
            normalized = normalized.replace(',', '.');
        } else {
            normalized = normalized.replace(",", "");
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.length() >= 10 ? raw.substring(0, 10) : raw;
        try {
            if (value.charAt(2) == '.') {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            }
            return LocalDate.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String digits(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }
}
