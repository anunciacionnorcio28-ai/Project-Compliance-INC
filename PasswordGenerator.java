package app.privmicro.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Generates the 32-character system password. A fresh 256-bit SecureRandom key is expanded with
 * HMAC-SHA256 keyed over the username (domain separation), then mapped to the alphabet with rejection
 * sampling so every character is uniformly distributed (~6.5 bits each, ~208 bits total).
 */
@Component
public class PasswordGenerator {
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*-_=+";
    public static final int LENGTH = 32;
    private final SecureRandom random = new SecureRandom();

    public String generate(String username) {
        try {
            byte[] key = new byte[32];
            random.nextBytes(key);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            int limit = 256 - (256 % ALPHABET.length());
            StringBuilder sb = new StringBuilder(LENGTH);
            int counter = 0;
            while (sb.length() < LENGTH) {
                mac.update(username.getBytes(StandardCharsets.UTF_8));
                mac.update((byte) counter++);
                for (byte b : mac.doFinal()) {
                    int v = b & 0xff;
                    if (v < limit && sb.length() < LENGTH) {
                        sb.append(ALPHABET.charAt(v % ALPHABET.length()));
                    }
                }
            }
            java.util.Arrays.fill(key, (byte) 0);
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
