package app.privmicro.service;

import app.privmicro.crypto.QrService;
import app.privmicro.crypto.TotpService;
import app.privmicro.dto.Dtos.*;
import app.privmicro.model.User;
import app.privmicro.repo.UserRepository;
import app.privmicro.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TotpService totp;
    private final QrService qr;
    private final OwnershipService ownership;
    private final LoginThrottle throttle;

    public AccountService(UserRepository users, PasswordEncoder encoder, TotpService totp, QrService qr,
                          OwnershipService ownership, LoginThrottle throttle) {
        this.users = users;
        this.encoder = encoder;
        this.totp = totp;
        this.qr = qr;
        this.ownership = ownership;
        this.throttle = throttle;
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthUser me) {
        User u = load(me);
        return new MeResponse(u.getUsername(), u.getPublicKey(), u.hasOwnership());
    }

    public ChallengeResponse challenge(AuthUser me, String action) {
        return new ChallengeResponse(ownership.challenge(me.id(), action));
    }

    /** MFA reset: starts a pending authenticator secret; the old one stays valid until the new one is confirmed. */
    @Transactional
    public MfaResetResponse mfaResetStart(AuthUser me, StepUpRequest req) {
        User u = stepUp(me, req, "mfa-reset");
        String secret = totp.newSecret();
        u.setPendingMfaSecret(secret);
        String uri = totp.otpauthUri(u.getUsername(), secret);
        return new MfaResetResponse(secret, uri, qr.dataUri(uri));
    }

    @Transactional
    public void mfaResetConfirm(AuthUser me, CodeRequest req) {
        throttle.check("step:" + me.id());
        User u = load(me);
        if (u.getPendingMfaSecret() == null) throw new ApiException(HttpStatus.CONFLICT, "No MFA reset in progress");
        long step = totp.verify(u.getPendingMfaSecret(), req.code(), 0);
        if (step < 0) {
            throttle.fail("step:" + me.id());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "That code didn't match the new secret");
        }
        u.setMfaSecret(u.getPendingMfaSecret());
        u.setPendingMfaSecret(null);
        u.setLastTotpStep(step);
    }

    @Transactional
    public OwnershipResponse ownershipEnable(AuthUser me, StepUpRequest req) {
        User u = load(me);
        if (u.hasOwnership()) throw new ApiException(HttpStatus.CONFLICT, "Owner Seed is already enabled");
        stepUp(me, req, "ownership-enable");
        OwnershipService.Seed seed = ownership.generate();
        ownership.apply(u, seed);
        return new OwnershipResponse(seed.mnemonic());
    }

    @Transactional
    public void ownershipDisable(AuthUser me, StepUpRequest req) {
        User u = stepUp(me, req, "ownership-disable");
        u.setOwnershipPublicKey(null);
        u.setOwnershipKeyHash(null);
    }

    /** Hard deletion: removes the user row; posts, comments, follows and media cascade in the database. */
    @Transactional
    public void deleteAccount(AuthUser me, StepUpRequest req) {
        User u = stepUp(me, req, "delete-account");
        users.delete(u);
    }

    private User load(AuthUser me) {
        return users.findById(me.id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired"));
    }

    /**
     * Re-authenticates with Factor 1 + Factor 2. If an Owner Seed is active, a valid signature over a fresh
     * server challenge is additionally required (conditional ownership validation).
     */
    private User stepUp(AuthUser me, StepUpRequest r, String action) {
        String key = "step:" + me.id();
        throttle.check(key);
        User u = load(me);
        boolean pwOk = encoder.matches(r.password(), u.getPasswordHash());
        long step = pwOk ? totp.verify(u.getMfaSecret(), r.code(), u.getLastTotpStep()) : -1;
        if (!pwOk || step < 0) {
            throttle.fail(key);
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Password or authenticator code is incorrect. If you just signed in, wait for the next code.");
        }
        if (u.hasOwnership() && !"ownership-enable".equals(action)
                && !ownership.verify(u, action, r.challenge(), r.signature())) {
            throttle.fail(key);
            throw new ApiException(HttpStatus.FORBIDDEN, "Owner Seed signature is missing, invalid or expired");
        }
        u.setLastTotpStep(step);
        throttle.success(key);
        return u;
    }
}
