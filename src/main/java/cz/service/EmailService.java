package cz.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class EmailService {

    // Pokud nemáš API klíč, zapne se automatická simulace do konzole
    private static final String RESEND_API_KEY = ""; // Zde si případně vložíš svůj klíč z resend.com

    private final RestClient restClient = RestClient.create();

    public void sendInvoiceNotification(String toEmail, String invoiceNumber, double amount, String issuerName) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            return;
        }

        // Pokud není vyplněný API klíč, e-mail se bezpečně simuluje do konzole
        if (RESEND_API_KEY == null || RESEND_API_KEY.trim().isEmpty()) {
            System.out.println(">> [SIMULACE RESEND API] Faktura č. " + invoiceNumber + " pro: " + toEmail + " (Částka: " + amount + " Kč)");
            return;
        }

        try {
            // Tělo e-mailu pro Resend API
            Map<String, Object> body = Map.of(
                    "from", "MiniFaktura <onboarding@resend.dev>", // Resend poskytuje testovací odesílatele zdarma
                    "to", new String[]{toEmail},
                    "subject", "Nová faktura č. " + invoiceNumber + " od " + issuerName,
                    "text", "Dobrý den,\n\nzasíláme vám novou fakturu č. " + invoiceNumber +
                            " na celkovou částku " + amount + " Kč.\n\nDěkujeme za spolupráci.\n" + issuerName
            );

            restClient.post()
                    .uri("https://api.resend.com/emails")
                    .header("Authorization", "Bearer " + RESEND_API_KEY)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            System.out.println(">> E-mail úspěšně odeslán přes Resend API na: " + toEmail);
        } catch (Exception e) {
            System.err.println(">> Chyba při odesílání e-mailu přes API: " + e.getMessage());
        }
    }
}