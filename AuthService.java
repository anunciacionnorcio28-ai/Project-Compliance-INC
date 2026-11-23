package app.privmicro.service;

import app.privmicro.crypto.PasswordGenerator;
import app.privmicro.crypto.QrService;
import app.privmicro.crypto.TotpService;
import app.privmicro.dto.Dtos.*;
import app.privmicro.model.User;
import app.privmicro.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

@Service
public class AuthService {
    private static final Set<String> RESERVED = Set.of("me", "admin", "root", "system", "support", "api", "null", "undefined");
    public static final String GENERIC_FAILURE = "Username, password or authenticator code is incorrect";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordGenerator passwords;
    private final TotpService totp;
    private final QrService qr;
    private final OwnershipService ownership;
    private final LoginThrottle throttle;
    private final String DUMMY_HASH; // equalises timing for unknown usernames

    public AuthService(UserRepository users, PasswordEncoder encoder, PasswordGenerator passwords, TotpService totp,
                       QrService qr, OwnershipService ownership, LoginThrottle throttle) {
        this.users = users;
        this.encoder = encoder;
        this.passwords = passwords;
        this.totp = totp;
        this.qr = qr;
        this.ownership = ownership;
        this.throttle = throttle;
        this.DUMMY_HASH = encoder.encode("not-a-real-password");
    }

    /** Step 1 of registration: no email, no PII. The system generates every credential. */
    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        throttle.limitGlobal("register", 30, 60_000);
        String username = req.username().toLowerCase(Locale.ROOT);
        if (RESERVED.contains(username)) throw new ApiException(HttpStatus.BAD_REQUEST, "That username is reserved");
        if (users.existsByUsername(username)) throw new ApiException(HttpStatus.CONFLICT, "That username is taken");
        validatePublicKey(req.publicKey());

        String password = passwords.generate(username);
        String secret = totp.newSecret();

        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setMfaSecret(secret);
        u.setPublicKey(req.publicKey());
        u.setMfaVerified(false);

        String mnemonic = null;
        if (req.enableOwnership()) {
            OwnershipService.Seed seed = ownership.generate();
            ownership.apply(u, seed);
            mnemonic = seed.mnemonic();
        }
        users.save(u);

        String uri = totp.otpauthUri(username, secret);
        return new RegisterResponse(username, password, secret, uri, qr.dataUri(uri), mnemonic);
    }

    /** Step 2: mandatory verification of the first TOTP code. Only then is the account active and a session issued. */
    @Transactional
    public User verifyRegistration(CredentialsRequest req) {
        String key = "reg:" + req.username().toLowerCase(Locale.ROOT);
        throttle.check(key);
        User u = users.findByUsername(req.username().toLowerCase(Locale.ROOT))
                .filter(x -> !x.isMfaVerified()).orElse(null);
        long step = -1;
        boolean pwOk = encoder.matches(req.password(), u != null ? u.getPasswordHash() : DUMMY_HASH);
        if (u != null && pwOk) step = totp.verify(u.getMfaSecret(), req.code(), u.getLastTotpStep());
        if (u == null || !pwOk || step < 0) {
            throttle.fail(key);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "That code didn't match. Check your authenticator app and try again.");
        }
        u.setMfaVerified(true);
        u.setLastTotpStep(step);
        throttle.success(key);
        return u;
    }

    /** Login: tokens are only issued after Factor 1 (password) AND Factor 2 (TOTP) are both valid. */
    @Transactional
    public User login(CredentialsRequest req) {
        String name = req.username().toLowerCase(Locale.ROOT);
        String key = "login:" + name;
        throttle.check(key);
        User u = users.findByUsername(name).filter(User::isMfaVerified).orElse(null);
        boolean pwOk = encoder.matches(req.password(), u != null ? u.getPasswordHash() : DUMMY_HASH);
        long step = (u != null && pwOk) ? totp.verify(u.getMfaSecret(), req.code(), u.getLastTotpStep()) : -1;
        if (u == null || !pwOk || step < 0) {
            throttle.fail(key);
            throw new ApiException(HttpStatus.UNAUTHORIZED, GENERIC_FAILURE);
        }
        u.setLastTotpStep(step);
        throttle.success(key);
        return u;
    }

    // ---------- Owner Seed recovery (only for accounts that opted in) ----------

    public ChallengeResponse recoveryChallenge(String username) {
        long id = users.findByUsername(username.toLowerCase(Locale.ROOT)).map(User::getId).orElse(0L);
        return new ChallengeResponse(ownership.challenge(id, "recover")); // same shape for unknown users: no enumeration
    }

    /** Signature by the Owner Seed resets password AND authenticator; the new secret must be confirmed. */
    @Transactional
    public RecoveryCompleteResponse recoveryComplete(RecoveryCompleteRequest req) {
        String name = req.username().toLowerCase(Locale.ROOT);
        String key = "recover:" + name;
        throttle.check(key);
        User u = users.findByUsername(name).filter(User::isMfaVerified).orElse(null);
        if (u == null || !ownership.verify(u, "recover", req.challenge(), req.signature())) {
            throttle.fail(key);
            throw new ApiException(HttpStatus.FORBIDDEN, "Recovery failed. Check your Owner Seed words.");
        }
        throttle.success(key);
        String password = passwords.generate(name);
        String secret = totp.newSecret();
        u.setPasswordHash(encoder.encode(password));
        u.setPendingMfaSecret(secret);
        String uri = totp.otpauthUri(name, secret);
        return new RecoveryCompleteResponse(password, secret, uri, qr.dataUri(uri));
    }

    @Transactional
    public User recoveryConfirm(CredentialsRequest req) {
        String name = req.username().toLowerCase(Locale.ROOT);
        String key = "recover-confirm:" + name;
        throttle.check(key);
        User u = users.findByUsername(name).filter(x -> x.getPendingMfaSecret() != null).orElse(null);
        boolean pwOk = encoder.matches(req.password(), u != null ? u.getPasswordHash() : DUMMY_HASH);
        long step = (u != null && pwOk) ? totp.verify(u.getPendingMfaSecret(), req.code(), 0) : -1;
        if (u == null || !pwOk || step < 0) {
            throttle.fail(key);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "That code didn't match the new authenticator secret.");
        }
        u.setMfaSecret(u.getPendingMfaSecret());
        u.setPendingMfaSecret(null);
        u.setLastTotpStep(step);
        throttle.success(key);
        return u;
    }

    private void validatePublicKey(String b64) {
        try {
            byte[] der = Base64.getDecoder().decode(b64);
            KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid encryption public key");
        }
    }
}
