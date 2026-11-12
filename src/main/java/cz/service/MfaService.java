package cz.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

@Service
public class MfaService {

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    // 1. Vygeneruje nový tajný klíč pro uživatele
    public String generateSecretKey() {
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        return key.getKey();
    }

    // 2. Ověří 6místný kód z mobilní aplikace
    public boolean verifyCode(String secretKey, String code) {
        if (secretKey == null || code == null || code.trim().isEmpty()) {
            return false;
        }
        try {
            int codeInt = Integer.parseInt(code.trim());
            return gAuth.authorize(secretKey, codeInt);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // 3. Vygeneruje QR kód ve formátu Base64 pro zobrazení v HTML šabloně
    public String generateQrCodeBase64(String email, String secretKey) {
        try {
            String otpauthUrl = String.format("otpauth://totp/MiniFaktura:%s?secret=%s&issuer=MiniFaktura", email, secretKey);
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(otpauthUrl, BarcodeFormat.QR_CODE, 200, 200);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Chyba při generování QR kódu", e);
        }
    }
}