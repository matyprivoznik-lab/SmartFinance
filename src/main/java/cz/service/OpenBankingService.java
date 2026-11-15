package cz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import cz.entity.BankConnection;
import cz.entity.Company;
import cz.repository.BankConnectionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OpenBankingService {

    static final String API = "https://bankaccountdata.gocardless.com/api/v2";
    private static final ZoneId ZONE = ZoneId.of("Europe/Prague");

    private final String secretId;
    private final String secretKey;
    private final BankConnectionRepository bankConnectionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();
    private String accessToken;
    private Instant accessUntil = Instant.EPOCH;

    public OpenBankingService(@Value("${app.bank.gocardless-secret-id:}") String secretId,
                              @Value("${app.bank.gocardless-secret-key:}") String secretKey,
                              BankConnectionRepository bankConnectionRepository) {
        this.secretId = secretId == null ? "" : secretId.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.bankConnectionRepository = bankConnectionRepository;
    }

    public boolean configured() {
        return !secretId.isEmpty() && !secretKey.isEmpty();
    }

    public List<Institution> institutions() {
        if (!configured()) {
            return catalog();
        }
        try {
            JsonNode body = objectMapper.readTree(get("/institutions/?country=cz"));
            List<Institution> live = new ArrayList<>();
            if (body.isArray()) {
                for (JsonNode node : body) {
                    String id = node.path("id").asText("");
                    String name = node.path("name").asText("");
                    if (!id.isBlank() && !name.isBlank()) {
                        live.add(new Institution(id, name));
                    }
                }
            }
            return live.isEmpty() ? catalog() : live;
        } catch (Exception ex) {
            return catalog();
        }
    }

    @Transactional
    public String begin(Company company, String institutionId, String institutionName, String redirectUrl) {
        if (!configured()) {
            throw new IllegalArgumentException("Propojení libovolné banky potřebuje klíče GoCardless v GOCARDLESS_SECRET_ID a GOCARDLESS_SECRET_KEY. Než je doplníš, platby načteš z výpisu nebo tokenem Fio.");
        }
        if (company == null || institutionId == null || institutionId.isBlank()) {
            throw new IllegalArgumentException("Vyber banku, kterou chceš propojit.");
        }
        String reference = UUID.randomUUID().toString();
        String callback = redirectUrl + (redirectUrl.contains("?") ? "&" : "?") + "ref=" + reference;
        JsonNode agreement = post("/agreements/enduser/", agreementBody(institutionId));
        JsonNode requisition = post("/requisitions/", requisitionBody(institutionId, agreement.path("id").asText(), reference, callback));
        BankConnection connection = new BankConnection();
        connection.setCompany(company);
        connection.setInstitutionId(institutionId.trim());
        connection.setInstitutionName(institutionName == null || institutionName.isBlank() ? institutionId : institutionName.trim());
        connection.setRequisitionId(requisition.path("id").asText());
        connection.setReference(reference);
        connection.setStatus(BankConnection.PENDING);
        connection.setStatusDetail("Čeká se na přihlášení v bance.");
        bankConnectionRepository.save(connection);
        String link = requisition.path("link").asText("");
        if (link.isBlank()) {
            throw new IllegalArgumentException("Banka nevrátila odkaz na přihlášení.");
        }
        return link;
    }

    @Transactional
    public BankConnection complete(Company company, String reference) {
        BankConnection connection = bankConnectionRepository.findByReference(reference)
                .filter(item -> item.getCompany() != null && company != null && item.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Tohle propojení u aktivní firmy není."));
        if (!configured()) {
            throw new IllegalArgumentException("Klíče k bankovnímu propojení chybí, stav účtu nejde ověřit.");
        }
        JsonNode requisition = readJson(get("/requisitions/" + connection.getRequisitionId() + "/"));
        JsonNode accounts = requisition.path("accounts");
        if (!accounts.isArray() || accounts.isEmpty()) {
            connection.setStatus(BankConnection.PENDING);
            connection.setStatusDetail("Banka ještě nevrátila účet. Dokonči přihlášení a zkus to znovu.");
            return bankConnectionRepository.save(connection);
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode account : accounts) {
            if (!account.asText("").isBlank()) {
                ids.add(account.asText());
            }
        }
        connection.setAccountIds(String.join(",", ids));
        connection.setStatus(BankConnection.LINKED);
        connection.setLinkedAt(LocalDateTime.now(ZONE));
        connection.setStatusDetail("Účet je propojený. Jde z něj číst, jestli platba přišla.");
        return bankConnectionRepository.save(connection);
    }

    public List<PaymentMatchService.BankRow> transactions(BankConnection connection, LocalDate from, LocalDate to) {
        if (connection == null || !connection.isLinked() || connection.getAccountIds() == null) {
            return List.of();
        }
        List<PaymentMatchService.BankRow> rows = new ArrayList<>();
        for (String accountId : connection.getAccountIds().split(",")) {
            if (accountId.isBlank()) {
                continue;
            }
            String body = get("/accounts/" + accountId.trim() + "/transactions/?date_from=" + from + "&date_to=" + to);
            rows.addAll(parseBooked(body));
        }
        return rows;
    }

    public static List<PaymentMatchService.BankRow> parseBooked(String json) {
        List<PaymentMatchService.BankRow> rows = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return rows;
        }
        try {
            JsonNode booked = new ObjectMapper().readTree(json).path("transactions").path("booked");
            if (!booked.isArray()) {
                return rows;
            }
            for (JsonNode transaction : booked) {
                double amount = transaction.path("transactionAmount").path("amount").asDouble(0);
                if (amount <= 0) {
                    continue;
                }
                String symbol = BankStatementParser.variableSymbol(textOf(transaction));
                if (symbol.isEmpty()) {
                    continue;
                }
                String date = transaction.path("bookingDate").asText(transaction.path("valueDate").asText(""));
                LocalDate bookedOn = date.length() >= 10 ? LocalDate.parse(date.substring(0, 10)) : null;
                rows.add(new PaymentMatchService.BankRow(bookedOn, amount, symbol));
            }
        } catch (Exception ignored) {
            return rows;
        }
        return rows;
    }

    static List<Institution> catalog() {
        return List.of(
                new Institution("CESKA_SPORITELNA_GIBACZPX", "Česká spořitelna"),
                new Institution("CSOB_CEKOCZPP", "ČSOB"),
                new Institution("KOMERCNI_BANKA_KOMBCZPP", "Komerční banka"),
                new Institution("FIO_FIOBCZPP", "Fio banka"),
                new Institution("AIRBANK_AIRACZPP", "Air Bank"),
                new Institution("MONETA_MONEY_BANK_AGBACZPP", "Moneta Money Bank"),
                new Institution("RAIFFEISENBANK_RZBCCZPP", "Raiffeisenbank"),
                new Institution("UNICREDIT_BANK_CZ_BACXCZPP", "UniCredit Bank"),
                new Institution("MBANK_BREXCZPP", "mBank"),
                new Institution("CREDITAS_CTASCZ22", "Banka Creditas"),
                new Institution("MAX_BANKA_MAXBCZPP", "Max banka"),
                new Institution("PARTNERS_BANKA_PTBNCZPP", "Partners Banka"),
                new Institution("TRINITY_BANK_TRBACZPP", "Trinity Bank"),
                new Institution("REVOLUT_REVOLT21", "Revolut")
        );
    }

    private String get(String path) {
        try {
            return restClient.get()
                    .uri(API + path)
                    .header("Authorization", "Bearer " + token())
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Banka teď seznam pohybů nevrátila. Výpis můžeš nahrát ručně.");
        }
    }

    private JsonNode post(String path, ObjectNode body) {
        try {
            String response = restClient.post()
                    .uri(API + path)
                    .header("Authorization", "Bearer " + token())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return readJson(response);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Propojení s bankou se nepodařilo založit. Zkontroluj klíče a zkus jinou banku.");
        }
    }

    private synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(accessUntil)) {
            return accessToken;
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("secret_id", secretId);
        body.put("secret_key", secretKey);
        try {
            String response = restClient.post()
                    .uri(API + "/token/new/")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            JsonNode json = readJson(response);
            accessToken = json.path("access").asText("");
            int seconds = json.path("access_expires").asInt(3600);
            accessUntil = Instant.now().plusSeconds(Math.max(60, seconds - 60));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Klíče GoCardless banka nepřijala. GOCARDLESS_SECRET_ID a GOCARDLESS_SECRET_KEY musí být z Bank Account Data.");
        }
        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("Banka nevrátila přístup pro čtení plateb.");
        }
        return accessToken;
    }

    private ObjectNode agreementBody(String institutionId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("institution_id", institutionId);
        body.put("max_historical_days", 90);
        body.put("access_valid_for_days", 90);
        body.putArray("access_scope").add("balances").add("details").add("transactions");
        return body;
    }

    private ObjectNode requisitionBody(String institutionId, String agreementId, String reference, String redirect) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("redirect", redirect);
        body.put("institution_id", institutionId);
        body.put("reference", reference);
        body.put("user_language", "CS");
        if (agreementId != null && !agreementId.isBlank()) {
            body.put("agreement", agreementId);
        }
        return body;
    }

    private JsonNode readJson(String response) {
        try {
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Odpověď banky nešla přečíst.");
        }
    }

    private static String textOf(JsonNode transaction) {
        StringBuilder text = new StringBuilder();
        append(text, transaction.path("remittanceInformationUnstructured").asText(""));
        JsonNode array = transaction.path("remittanceInformationUnstructuredArray");
        if (array.isArray()) {
            array.forEach(item -> append(text, item.asText("")));
        }
        append(text, transaction.path("remittanceInformationStructured").toString());
        append(text, transaction.path("endToEndId").asText(""));
        append(text, transaction.path("additionalInformation").asText(""));
        return text.toString();
    }

    private static void append(StringBuilder text, String value) {
        if (value != null && !value.isBlank()) {
            text.append(' ').append(value);
        }
    }

    public record Institution(String id, String name) {
    }
}
