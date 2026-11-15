package cz.document;

import cz.entity.Invoice;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class PublicLinks {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PublicLinks() {
    }

    public static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static void ensure(Invoice invoice) {
        if (invoice != null && (invoice.getPublicToken() == null || invoice.getPublicToken().isBlank())) {
            invoice.setPublicToken(newToken());
        }
    }
}
