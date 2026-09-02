package tn.defense.gamma3.auth.service;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;

public class TotpUtils {

    public static String generateSecretKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        Base32 base32 = new Base32();
        return base32.encodeToString(bytes);
    }

    public static boolean verifyCode(String secret, int code) {
        if (secret == null || secret.isEmpty()) {
            return false;
        }
        Base32 base32 = new Base32();
        byte[] decodedKey = base32.decode(secret);
        long timeWindow = System.currentTimeMillis() / 1000L / 30L;

        // Permettre une marge d'erreur de 1 fenêtre de temps avant/après pour la désynchronisation
        for (int i = -1; i <= 1; ++i) {
            long hash = getHash(decodedKey, timeWindow + i);
            if (hash == code) {
                return true;
            }
        }
        return false;
    }

    private static long getHash(byte[] key, long t) {
        byte[] data = new byte[8];
        long value = t;
        for (int i = 8; i-- > 0; value >>>= 8) {
            data[i] = (byte) value;
        }

        SecretKeySpec signKey = new SecretKeySpec(key, "HmacSHA1");
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signKey);
            byte[] hash = mac.doFinal(data);

            int offset = hash[20 - 1] & 0xF;
            long truncatedHash = 0;
            for (int i = 0; i < 4; ++i) {
                truncatedHash <<= 8;
                truncatedHash |= (hash[offset + i] & 0xFF);
            }
            truncatedHash &= 0x7FFFFFFF;
            truncatedHash %= 1000000;
            return truncatedHash;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
