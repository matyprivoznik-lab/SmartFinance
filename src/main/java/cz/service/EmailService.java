package cz.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class EmailService {

    private final String resendApiKey;
    private final String from;
    private final RestClient restClient = RestClient.create();

    public EmailService(@Value("${app.mail.resend-api-key:}") String resendApiKey,
                        @Value("${app.mail.from:MiniFaktura <onboarding@resend.dev>}") String from) {
        this.resendApiKey = resendApiKey == null ? "" : resendApiKey.trim();
        this.from = from;
    }

    public void sendInvoiceNotification(String toEmail, String invoiceNumber, double amount, String issuerName) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            return;
        }

        if (resendApiKey.isEmpty()) {
            System.out.println(">> [SIMULACE RESEND API] Faktura č. " + invoiceNumber + " pro: " + toEmail + " (Částka: " + amount + " Kč)");
            return;
        }

        try {
            // Tělo e-mailu pro Resend API
            Map<String, Object> body = Map.of(
                    "from", from,
                    "to", new String[]{toEmail},
                    "subject", "Nová faktura č. " + invoiceNumber + " od " + issuerName,
                    "text", "Dobrý den,\n\nzasíláme vám novou fakturu č. " + invoiceNumber +
                            " na celkovou částku " + amount + " Kč.\n\nDěkujeme za spolupráci.\n" + issuerName
            );

            restClient.post()
                    .uri("https://api.resend.com/emails")
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            System.out.println(">> E-mail úspěšně odeslán přes Resend API na: " + toEmail);
        } catch (Exception e) {
            System.err.println(">> Chyba při odesílání e-mailu přes API: " + e.getMessage());
        }
    }

    public void sendPlain(String toEmail, String subject, String text) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        if (resendApiKey.isEmpty()) {
            System.out.println(">> [SIMULACE RESEND API] " + subject + " pro " + toEmail);
            return;
        }
        try {
            restClient.post()
                    .uri("https://api.resend.com/emails")
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .body(java.util.Map.of(
                            "from", from,
                            "to", new String[]{toEmail},
                            "subject", subject,
                            "text", text == null ? "" : text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            System.err.println(">> Chyba při odesílání e-mailu: " + ex.getMessage());
        }
    }

    public void sendVerificationCode(String toEmail, String code) {
        String text = "Dobrý den,\n\nověřovací kód pro MiniFakturu je " + code
                + ".\nPlatí 15 minut. Pokud jsi účet nezakládal, e-mail ignoruj.\n";
        if (resendApiKey.isEmpty()) {
            System.out.println(">> [SIMULACE RESEND API] Ověřovací kód pro " + toEmail + ": " + code);
            return;
        }
        try {
            Map<String, Object> body = Map.of(
                    "from", from,
                    "to", new String[]{toEmail},
                    "subject", "Ověřovací kód MiniFaktura",
                    "text", text
            );
            restClient.post()
                    .uri("https://api.resend.com/emails")
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.err.println(">> Chyba při odesílání ověřovacího kódu: " + e.getMessage());
            System.out.println(">> [ZÁLOHA] Ověřovací kód pro " + toEmail + ": " + code);
        }
    }
}