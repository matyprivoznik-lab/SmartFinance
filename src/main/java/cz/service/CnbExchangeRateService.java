package cz.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class CnbExchangeRateService {

    private static final DateTimeFormatter CNB_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String URL = "https://www.cnb.cz/cs/financni-trhy/devizovy-trh/kurzy-devizoveho-trhu/kurzy-devizoveho-trhu/denni_kurz.txt?date=";

    public Optional<Rate> rate(String currency, LocalDate date) {
        if (currency == null || currency.isBlank() || "CZK".equalsIgnoreCase(currency)) {
            return Optional.of(new Rate("CZK", 1.0, date == null ? LocalDate.now() : date));
        }
        LocalDate cursor = date == null ? LocalDate.now() : date;
        RestTemplate restTemplate = new RestTemplate();
        for (int attempt = 0; attempt < 7; attempt++) {
            try {
                String body = restTemplate.getForObject(URL + cursor.format(CNB_DATE), String.class);
                Optional<Rate> parsed = parse(body, currency, cursor);
                if (parsed.isPresent()) {
                    return parsed;
                }
            } catch (Exception ignored) {
                // ČNB o víkendu a svátcích denní kurz nevydává. Zkusíme předchozí den.
            }
            cursor = cursor.minusDays(1);
        }
        return Optional.empty();
    }

    public static Optional<Rate> parse(String body, String currency, LocalDate requested) {
        if (body == null || currency == null) {
            return Optional.empty();
        }
        String code = currency.trim().toUpperCase();
        for (String line : body.split("\\R")) {
            String[] columns = line.split("\\|");
            if (columns.length < 5 || !code.equalsIgnoreCase(columns[3].trim())) {
                continue;
            }
            int amount;
            try {
                amount = Integer.parseInt(columns[2].trim());
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
            if (amount <= 0) {
                return Optional.empty();
            }
            double quoted = Double.parseDouble(columns[4].trim().replace(',', '.'));
            return Optional.of(new Rate(code, quoted / amount, requested));
        }
        return Optional.empty();
    }

    public record Rate(String currency, double czkPerUnit, LocalDate date) {
    }
}
