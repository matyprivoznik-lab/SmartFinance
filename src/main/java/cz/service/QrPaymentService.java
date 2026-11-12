package cz.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import cz.entity.Invoice;
import cz.entity.User;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QrPaymentService {

    private static final Pattern CZECH_ACCOUNT = Pattern.compile("^(?:(\\d{1,6})-)?(\\d{1,10})/(\\d{4})$");
    private static final Pattern IBAN = Pattern.compile("^CZ\\d{22}$");
    private static final DateTimeFormatter DUE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * Czech domestic account ({@code [prefix-]number/bank}) or an existing CZ IBAN.
     * Returns a compact IBAN, or null when the account cannot be used for QR Platba.
     */
    public String paymentIban(User user) {
        if (user == null) {
            return null;
        }
        String fromIban = normalizeIban(user.getIban());
        if (fromIban != null) {
            return fromIban;
        }
        return czechAccountToIban(user.getBankAccount());
    }

    public String spdPayload(User user, Invoice invoice) {
        String iban = paymentIban(user);
        if (iban == null || invoice == null) {
            return null;
        }

        StringBuilder payload = new StringBuilder("SPD*1.0*ACC:");
        payload.append(iban);
        String swift = normalizeSwift(user.getSwift());
        if (swift != null) {
            payload.append('+').append(swift);
        }
        payload.append('*');

        BigDecimal amount = amount(invoice);
        if (amount != null) {
            payload.append("AM:").append(amount.toPlainString()).append('*');
        }
        payload.append("CC:CZK*");

        if (invoice.getDueDate() != null) {
            payload.append("DT:").append(invoice.getDueDate().format(DUE_DATE)).append('*');
        }

        String recipient = spaydText(user.getName(), 35);
        if (recipient != null) {
            payload.append("RN:").append(recipient).append('*');
        }

        String message = spaydText(invoiceMessage(invoice), 60);
        if (message != null) {
            payload.append("MSG:").append(message).append('*');
        }

        String variableSymbol = digits(invoice.getVariableSymbol(), 10);
        if (variableSymbol != null) {
            payload.append("X-VS:").append(variableSymbol).append('*');
        }
        String constantSymbol = digits(invoice.getConstantSymbol(), 10);
        if (constantSymbol != null) {
            payload.append("X-KS:").append(constantSymbol).append('*');
        }
        return payload.toString();
    }

    /** Data URL for an {@code img} tag, or null when a payment QR cannot be built. */
    public String qrCodeDataUrl(User user, Invoice invoice) {
        String payload = spdPayload(user, invoice);
        if (payload == null) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 240, 240, hints);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    static String czechAccountToIban(String account) {
        if (account == null) {
            return null;
        }
        String compact = account.replace(" ", "").replace("\u00a0", "");
        String existing = normalizeIban(compact);
        if (existing != null) {
            return existing;
        }
        Matcher matcher = CZECH_ACCOUNT.matcher(compact);
        if (!matcher.matches()) {
            return null;
        }
        String prefix = matcher.group(1) == null ? "0" : matcher.group(1);
        String number = matcher.group(2);
        String bank = matcher.group(3);
        String bban = leftPad(bank, 4) + leftPad(prefix, 6) + leftPad(number, 10);
        int check = 98 - mod97(bban + "123500");
        String iban = "CZ" + String.format("%02d", check) + bban;
        return normalizeIban(iban);
    }

    private static String normalizeIban(String value) {
        if (value == null) {
            return null;
        }
        String compact = value.replace(" ", "").replace("\u00a0", "").toUpperCase();
        if (!IBAN.matcher(compact).matches()) {
            return null;
        }
        String rearranged = compact.substring(4) + toDigits(compact.substring(0, 4));
        return mod97(rearranged) == 1 ? compact : null;
    }

    private static String toDigits(String value) {
        StringBuilder digits = new StringBuilder(value.length() * 2);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                digits.append(c - 'A' + 10);
            } else {
                return "";
            }
        }
        return digits.toString();
    }

    private static String normalizeSwift(String swift) {
        if (swift == null) {
            return null;
        }
        String compact = swift.replace(" ", "").toUpperCase();
        return compact.matches("[A-Z0-9]{8}([A-Z0-9]{3})?") ? compact : null;
    }

    private static BigDecimal amount(Invoice invoice) {
        Double gross = invoice.getGrossTotal();
        if (gross == null || gross <= 0) {
            return null;
        }
        BigDecimal scaled = BigDecimal.valueOf(gross).setScale(2, RoundingMode.HALF_UP);
        if (scaled.signum() <= 0 || scaled.toPlainString().length() > 10) {
            return null;
        }
        return scaled;
    }

    private static String invoiceMessage(Invoice invoice) {
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            return "Faktura";
        }
        return "Faktura " + invoice.getInvoiceNumber().trim();
    }

    private static String digits(String value, int max) {
        if (value == null) {
            return null;
        }
        String compact = value.replace(" ", "");
        if (!compact.matches("\\d{1," + max + "}")) {
            return null;
        }
        return compact;
    }

    private static String spaydText(String value, int max) {
        if (value == null) {
            return null;
        }
        String stripped = stripDiacritics(value)
                .replace('*', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (stripped.isEmpty()) {
            return null;
        }
        return stripped.length() <= max ? stripped : stripped.substring(0, max).trim();
    }

    private static String stripDiacritics(String value) {
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private static String leftPad(String value, int length) {
        String digits = value.replaceFirst("^0+(?!$)", "");
        if (digits.length() > length) {
            return digits.substring(digits.length() - length);
        }
        return "0".repeat(length - digits.length()) + digits;
    }

    private static int mod97(String digits) {
        int remainder = 0;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Non-digit in IBAN checksum");
            }
            remainder = (remainder * 10 + (c - '0')) % 97;
        }
        return remainder;
    }
}
