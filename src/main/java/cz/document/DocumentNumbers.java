package cz.document;

import java.time.LocalDate;
import java.util.Collection;
import java.util.regex.Pattern;

public final class DocumentNumbers {

    public static final String DEFAULT_PATTERN = "DRUHRRRRMM-CCC";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9\\-/]{1,24}");

    private DocumentNumbers() {
    }

    public static boolean isValid(String pattern) {
        if (pattern == null || !SAFE.matcher(pattern).matches() || pattern.indexOf('C') < 0) {
            return false;
        }
        int start = pattern.indexOf('C');
        int end = start;
        while (end < pattern.length() && pattern.charAt(end) == 'C') {
            end++;
        }
        return pattern.indexOf('C', end) < 0;
    }

    public static String next(String pattern, DocumentKind kind, LocalDate date, Collection<String> existing) {
        String used = isValid(pattern) ? pattern : DEFAULT_PATTERN;
        if (!used.contains("DRUH") && kind != null && kind != DocumentKind.INVOICE) {
            used = kind.getSeriesCode() + used;
        }
        int start = used.indexOf('C');
        int end = start;
        while (end < used.length() && used.charAt(end) == 'C') {
            end++;
        }
        String before = expand(used.substring(0, start), kind, date);
        String after = expand(used.substring(end), kind, date);
        int width = end - start;
        int sequence = 1;
        if (existing != null) {
            for (String number : existing) {
                if (number == null || !number.startsWith(before) || !number.endsWith(after)) {
                    continue;
                }
                if (number.length() < before.length() + after.length()) {
                    continue;
                }
                String middle = number.substring(before.length(), number.length() - after.length());
                if (!middle.matches("\\d+")) {
                    continue;
                }
                int value = Integer.parseInt(middle);
                if (value >= sequence) {
                    sequence = value + 1;
                }
            }
        }
        String digits = Integer.toString(sequence);
        if (digits.length() < width) {
            digits = "0".repeat(width - digits.length()) + digits;
        }
        return before + digits + after;
    }

    private static String expand(String token, DocumentKind kind, LocalDate date) {
        LocalDate used = date == null ? LocalDate.now() : date;
        return token
                .replace("DRUH", kind == null ? "" : kind.getSeriesCode())
                .replace("RRRR", Integer.toString(used.getYear()))
                .replace("MM", String.format("%02d", used.getMonthValue()));
    }
}
