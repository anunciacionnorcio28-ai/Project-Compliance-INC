package app.privmicro.service;

import app.privmicro.config.AppProperties;
import app.privmicro.model.User;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional Tier-3 "Owner Seed". A 256-bit seed is drawn from SecureRandom, shown once as a 32-word mnemonic,
 * and only its Ed25519 public key is stored. High-privilege actions then require a signed, single-use,
 * short-lived server challenge (challenge-response).
 */
@Service
public class OwnershipService {
    public static final Set<String> ACTIONS = Set.of("mfa-reset", "ownership-disable", "delete-account", "recover");
    private static final long CHALLENGE_TTL_SEC = 120;

    private final SecureRandom random = new SecureRandom();
    private final byte[] macKey;
    private final List<String> words;
    private final ConcurrentHashMap<String, Long> usedNonces = new ConcurrentHashMap<>();

    public record Seed(String mnemonic, String publicKeyBase64, String keyHash) {}

    public OwnershipService(AppProperties props) {
        try {
            this.macKey = MessageDigest.getInstance("SHA-256")
                    .digest(("challenge|" + props.jwtSecret()).getBytes(StandardCharsets.UTF_8));
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    getClass().getResourceAsStream("/wordlist.txt"), StandardCharsets.UTF_8))) {
                this.words = r.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
            }
            if (words.size() != 256) throw new IllegalStateException("wordlist must contain 256 words");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Seed generate() {
        byte[] seed = new byte[32];
        random.nextBytes(seed);
        byte[] pub = new Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().getEncoded();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < seed.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words.get(seed[i] & 0xff));
        }
        Arrays.fill(seed, (byte) 0);
        return new Seed(sb.toString(), Base64.getEncoder().encodeToString(pub), sha256Hex(pub));
    }

    public void apply(User user, Seed seed) {
        user.setOwnershipPublicKey(seed.publicKeyBase64());
        user.setOwnershipKeyHash(seed.keyHash());
    }

    /** Challenge format: userId.action.nonce.expiresEpochSec.mac */
    public String challenge(long userId, String action) {
        if (!ACTIONS.contains(action)) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown action");
        byte[] n = new byte[16];
        random.nextBytes(n);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(n);
        long exp = System.currentTimeMillis() / 1000 + CHALLENGE_TTL_SEC;
        String payload = userId + "." + action + "." + nonce + "." + exp;
        return payload + "." + mac(payload);
    }

    public boolean verify(User user, String action, String challenge, String signatureB64) {
        try {
            if (!user.hasOwnership() || challenge == null || signatureB64 == null) return false;
            String[] p = challenge.split("\\.");
            if (p.length != 5) return false;
            String payload = p[0] + "." + p[1] + "." + p[2] + "." + p[3];
            if (!MessageDigest.isEqual(mac(payload).getBytes(StandardCharsets.UTF_8), p[4].getBytes(StandardCharsets.UTF_8)))
                return false;
            if (Long.parseLong(p[0]) != user.getId() || !p[1].equals(action)) return false;
            if (Long.parseLong(p[3]) < System.currentTimeMillis() / 1000) return false;
            byte[] pub = Base64.getDecoder().decode(user.getOwnershipPublicKey());
            byte[] sig = Base64.getDecoder().decode(signatureB64);
            Ed25519Signer signer = new Ed25519Signer();
            signer.init(false, new Ed25519PublicKeyParameters(pub, 0));
            byte[] msg = challenge.getBytes(StandardCharsets.UTF_8);
            signer.update(msg, 0, msg.length);
            if (!signer.verifySignature(sig)) return false;
            // single use
            return usedNonces.putIfAbsent(p[2], System.currentTimeMillis()) == null;
        } catch (Exception e) {
            return false;
        }
    }

    public void purgeNonces() {
        long cutoff = System.currentTimeMillis() - (CHALLENGE_TTL_SEC + 60) * 1000;
        usedNonces.entrySet().removeIf(e -> e.getValue() < cutoff);
    }

    private String mac(String payload) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(macKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(m.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
