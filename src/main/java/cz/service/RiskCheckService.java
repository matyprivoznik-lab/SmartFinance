package cz.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Service
public class RiskCheckService {

    static final String ISIR_URL = "https://isir.justice.cz/isir/ueu/vysledek_lustrace.do?ic=";
    static final String UNRELIABLE_URL = "https://www.financnisprava.cz/assets/cs/prilohy/d-seznam-nespolehlivych-platcu/nespolehlivi_platci.csv";

    public record Risk(String insolvency, String insolvencyDetail, Boolean unreliableVat) {
    }

    public Risk check(String ico, String vatNumber) {
        String insolvencyBody = fetch(ISIR_URL + digits(ico));
        String registry = fetch(UNRELIABLE_URL);
        return new Risk(
                insolvency(insolvencyBody).name(),
                insolvencyDetail(insolvencyBody),
                registry == null ? null : listedAsUnreliable(registry, ico, vatNumber));
    }

    public enum Insolvency {
        OK, FOUND, UNKNOWN
    }

    public static Insolvency insolvency(String body) {
        if (body == null || body.isBlank()) {
            return Insolvency.UNKNOWN;
        }
        String text = body.toLowerCase(Locale.ROOT);
        if (text.contains("nebyly nalezeny") || text.contains("nebyl nalezen") || text.contains("0 záznam")) {
            return Insolvency.OK;
        }
        if (text.contains("ins ") || text.contains("spisová značka") || text.contains("insolven")) {
            return Insolvency.FOUND;
        }
        return Insolvency.UNKNOWN;
    }

    public static String insolvencyDetail(String body) {
        Insolvency state = insolvency(body);
        if (state == Insolvency.FOUND) {
            return "V insolvenčním rejstříku je k IČO záznam. Ověř spis, než na firmu fakturuješ.";
        }
        if (state == Insolvency.OK) {
            return "Insolvenční rejstřík pro tohle IČO nic nenašel.";
        }
        return "Insolvenční rejstřík teď neodpověděl. Stav neber jako čistý.";
    }

    public static boolean listedAsUnreliable(String registry, String ico, String vatNumber) {
        if (registry == null) {
            return false;
        }
        String haystack = registry.replace(" ", "");
        String icoDigits = digits(ico);
        String dic = vatNumber == null ? "" : vatNumber.replace(" ", "").toUpperCase(Locale.ROOT);
        return (!icoDigits.isEmpty() && haystack.contains(icoDigits))
                || (!dic.isEmpty() && haystack.toUpperCase(Locale.ROOT).contains(dic));
    }

    private static String fetch(String url) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.setRequestFactory(factory());
            return restTemplate.getForObject(url, String.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private static org.springframework.http.client.SimpleClientHttpRequestFactory factory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(8));
        return factory;
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public static Optional<String> label(Boolean unreliable) {
        if (unreliable == null) {
            return Optional.of("Seznam nespolehlivých plátců se nepodařilo stáhnout.");
        }
        if (unreliable) {
            return Optional.of("Odběratel je v seznamu nespolehlivých plátců DPH. Za neodvedenou daň můžeš ručit.");
        }
        return Optional.of("V seznamu nespolehlivých plátců DPH není.");
    }
}
