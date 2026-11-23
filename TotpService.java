package app.privmicro.crypto;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** TOTP (RFC 6238) on top of the googleauth library, with replay protection by time-step. */
@Service
public class TotpService {
    private static final long STEP_MS = 30_000L;
    private static final String ISSUER = "PrivMicro";
    private final SecureRandom random = new SecureRandom();
    private final GoogleAuthenticator gauth = new GoogleAuthenticator(
            new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                    .setCodeDigits(6)
                    .setTimeStepSizeInMillis(STEP_MS)
                    .build());

    /** 160-bit secret, base32 without padding. */
    public String newSecret() {
        byte[] b = new byte[20];
        random.nextBytes(b);
        return new Base32().encodeToString(b).replace("=", "");
    }

    public String otpauthUri(String username, String secret) {
        String label = URLEncoder.encode(ISSUER + ":" + username, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + ISSUER
                + "&algorithm=SHA1&digits=6&period=30";
    }

    /**
     * Verifies a code within +-1 time step. Returns the matched step (> lastStep) or -1 when invalid/replayed.
     */
    public long verify(String secret, String code, long lastStep) {
        if (code == null || !code.matches("\\d{6}")) {
            return -1;
        }
        long nowStep = System.currentTimeMillis() / STEP_MS;
        long matched = -1;
        byte[] given = code.getBytes(StandardCharsets.UTF_8);
        for (long off = -1; off <= 1; off++) {
            long step = nowStep + off;
            int expected = gauth.getTotpPassword(secret, step * STEP_MS);
            byte[] exp = String.format("%06d", expected).getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(exp, given) && step > lastStep) {
                matched = Math.max(matched, step);
            }
        }
        return matched;
    }
}
