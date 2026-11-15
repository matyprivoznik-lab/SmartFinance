package cz.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BankStatementParser {

    private static final Pattern VARIABLE_SYMBOL = Pattern.compile("(?:/VS|VS)[:\\s/-]*(\\d{1,10})", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter GPC_DATE = DateTimeFormatter.ofPattern("ddMMyy");

    private BankStatementParser() {
    }

    public static List<PaymentMatchService.BankRow> parse(byte[] bytes, String filename) {
        if (bytes == null || bytes.length == 0) {
            return List.of();
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
        String trimmed = utf8.stripLeading();
        if (trimmed.startsWith("<")) {
            return parseCamt(utf8);
        }
        if (looksLikeGpc(utf8)) {
            return parseGpc(utf8);
        }
        List<PaymentMatchService.BankRow> rows = PaymentMatchService.parseCsv(utf8);
        if (rows.isEmpty()) {
            String cp1250 = new String(bytes, Charset.forName("windows-1250"));
            if (looksLikeGpc(cp1250)) {
                return parseGpc(cp1250);
            }
            return PaymentMatchService.parseCsv(cp1250);
        }
        return rows;
    }

    public static List<PaymentMatchService.BankRow> parseGpc(String text) {
        List<PaymentMatchService.BankRow> rows = new ArrayList<>();
        if (text == null) {
            return rows;
        }
        for (String raw : text.split("\\R")) {
            String line = raw.length() >= 128 ? raw.substring(0, 128) : raw;
            if (!line.startsWith("075") || line.length() < 97) {
                continue;
            }
            String code = line.substring(60, 61);
            if ("1".equals(code)) {
                continue;
            }
            Double amount = halere(line.substring(48, 60));
            String symbol = digits(line.substring(61, Math.min(71, line.length())));
            if (amount == null || amount <= 0 || symbol.isEmpty()) {
                continue;
            }
            rows.add(new PaymentMatchService.BankRow(gpcDate(line.substring(91, 97)), amount, symbol));
        }
        return rows;
    }

    public static List<PaymentMatchService.BankRow> parseCamt(String xml) {
        List<PaymentMatchService.BankRow> rows = new ArrayList<>();
        if (xml == null || xml.isBlank()) {
            return rows;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList entries = document.getElementsByTagNameNS("*", "Ntry");
            if (entries.getLength() == 0) {
                entries = document.getElementsByTagName("Ntry");
            }
            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                String credit = text(entry, "CdtDbtInd");
                if ("DBIT".equalsIgnoreCase(credit)) {
                    continue;
                }
                Double amount = plainAmount(text(entry, "Amt"));
                String symbol = variableSymbol(entry.getTextContent());
                if (amount == null || amount <= 0 || symbol.isEmpty()) {
                    continue;
                }
                rows.add(new PaymentMatchService.BankRow(isoDate(text(entry, "Dt")), amount, symbol));
            }
        } catch (Exception ignored) {
            return rows;
        }
        return rows;
    }

    public static String variableSymbol(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = VARIABLE_SYMBOL.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String digits = digits(text);
        if (digits.length() >= 1 && digits.length() <= 10 && text.trim().matches("[\\d\\s./-]+")) {
            return digits;
        }
        return "";
    }

    private static boolean looksLikeGpc(String text) {
        for (String line : text.split("\\R")) {
            if (line.startsWith("075") && line.length() >= 97) {
                return true;
            }
        }
        return false;
    }

    private static Double halere(String raw) {
        String digits = digits(raw);
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(digits) / 100.0;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double plainAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim().replace(" ", "").replace(",", "."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDate gpcDate(String raw) {
        try {
            return LocalDate.parse(raw, GPC_DATE);
        } catch (Exception ex) {
            return null;
        }
    }

    private static LocalDate isoDate(String raw) {
        if (raw == null || raw.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(raw.substring(0, 10));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagName(localName);
        }
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
