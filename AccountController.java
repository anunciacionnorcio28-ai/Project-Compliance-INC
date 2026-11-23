package app.privmicro.web;

import app.privmicro.dto.Dtos.*;
import app.privmicro.security.AuthUser;
import app.privmicro.security.JwtService;
import app.privmicro.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService account;
    private final JwtService jwt;

    public AccountController(AccountService account, JwtService jwt) {
        this.account = account;
        this.jwt = jwt;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser me) {
        return account.me(me);
    }

    @PostMapping("/challenge")
    public ChallengeResponse challenge(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChallengeRequest req) {
        return account.challenge(me, req.action());
    }

    @PostMapping("/mfa/reset")
    public MfaResetResponse mfaReset(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody StepUpRequest req) {
        return account.mfaResetStart(me, req);
    }

    @PostMapping("/mfa/confirm")
    public ResponseEntity<Void> mfaConfirm(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CodeRequest req) {
        account.mfaResetConfirm(me, req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/ownership/enable")
    public OwnershipResponse ownershipEnable(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody StepUpRequest req) {
        return account.ownershipEnable(me, req);
    }

    @PostMapping("/ownership/disable")
    public ResponseEntity<Void> ownershipDisable(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody StepUpRequest req) {
        account.ownershipDisable(me, req);
        return ResponseEntity.noContent().build();
    }

    /** Hard account deletion. */
    @PostMapping("/delete")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody StepUpRequest req) {
        account.deleteAccount(me, req);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, jwt.clearCookie().toString()).build();
    }
}
