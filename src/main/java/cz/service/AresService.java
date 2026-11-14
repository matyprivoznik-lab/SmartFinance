package cz.service;

import cz.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@Service
public class AresService {

    // Správná koncová adresa backendového REST API ARES
    private static final String ARES_URL = "https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/ekonomicke-subjekty/";

    public AresResponse getCompanyDetails(String ico) {
        try {
            if (ico == null || ico.trim().isEmpty()) {
                return null;
            }

            String formattedIco = normalizeIco(ico);
            if (formattedIco == null) {
                return null;
            }

            RestTemplate restTemplate = new RestTemplate();
            String url = ARES_URL + formattedIco;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map<String, Object> response = responseEntity.getBody();

            if (response != null) {
                AresResponse details = new AresResponse();
                details.setIco(formattedIco);

                // Obchodní jméno
                details.setName((String) response.get("obchodniJmeno"));
                details.setVatNumber(text(response.get("dic")));

                // Získání adresy ze sekce 'sidlo'
                Map<String, Object> sidlo = (Map<String, Object>) response.get("sidlo");
                if (sidlo != null) {
                    // API ARES často vrací rovnou hotovou textovou adresu
                    String textovaAdresa = (String) sidlo.get("textovaAdresa");
                    if (textovaAdresa != null && !textovaAdresa.isEmpty()) {
                        details.setAddress(textovaAdresa);
                    } else {
                        // Případné ruční sestavení, kdyby textová adresa chyběla
                        String nazevUlice = (String) sidlo.get("nazevUlice");
                        Object cisloDomovniObj = sidlo.get("cisloDomovni");
                        String cisloDomovni = cisloDomovniObj != null ? String.valueOf(cisloDomovniObj) : "";

                        Object cisloOrientacniObj = sidlo.get("cisloOrientacni");
                        String cisloOrientacni = cisloOrientacniObj != null ? "/" + cisloOrientacniObj : "";

                        String obec = (String) sidlo.get("nazevObce");
                        Object pscObj = sidlo.get("psc");
                        String psc = pscObj != null ? String.valueOf(pscObj) : "";

                        StringBuilder addressBuilder = new StringBuilder();
                        if (nazevUlice != null && !nazevUlice.isEmpty()) {
                            addressBuilder.append(nazevUlice).append(" ");
                        }
                        addressBuilder.append(cisloDomovni).append(cisloOrientacni);
                        addressBuilder.append(", ").append(psc).append(" ").append(obec);

                        details.setAddress(addressBuilder.toString().trim());
                    }
                }
                return details;
            }
        } catch (Exception e) {
            System.err.println("Chyba při načítání z ARES: " + e.getMessage());
        }
        return null;
    }

    public static void applyTo(User user, AresResponse data) {
        user.setIco(data.getIco());
        user.setName(data.getName());
        user.setAddress(data.getAddress());
        user.setStreet(null);
        user.setCity(null);
        user.setZipCode(null);
        user.setVatNumber(blankToNull(data.getVatNumber()));
    }

    public static String normalizeIco(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.trim();
        if (!digits.matches("\\d{1,8}")) {
            return null;
        }
        return String.format("%08d", Long.parseLong(digits));
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static class AresResponse {
        private String ico;
        private String name;
        private String address;
        private String vatNumber;

        public String getIco() { return ico; }
        public void setIco(String ico) { this.ico = ico; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getVatNumber() { return vatNumber; }
        public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }
    }
}