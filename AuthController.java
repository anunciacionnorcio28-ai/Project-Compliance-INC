package app.privmicro.web;

import app.privmicro.dto.Dtos.*;
import app.privmicro.model.User;
import app.privmicro.security.JwtService;
import app.privmicro.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final JwtService jwt;

    public AuthController(AuthService auth, JwtService jwt) {
        this.auth = auth;
        this.jwt = jwt;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/register/verify")
    public ResponseEntity<MeResponse> verify(@Valid @RequestBody CredentialsRequest req) {
        return session(auth.verifyRegistration(req));
    }

    @PostMapping("/login")
    public ResponseEntity<MeResponse> login(@Valid @RequestBody CredentialsRequest req) {
        return session(auth.login(req));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, jwt.clearCookie().toString()).build();
    }

    @PostMapping("/recovery/challenge")
    public ChallengeResponse recoveryChallenge(@Valid @RequestBody RecoveryChallengeRequest req) {
        return auth.recoveryChallenge(req.username());
    }

    @PostMapping("/recovery/complete")
    public RecoveryCompleteResponse recoveryComplete(@Valid @RequestBody RecoveryCompleteRequest req) {
        return auth.recoveryComplete(req);
    }

    @PostMapping("/recovery/confirm")
    public ResponseEntity<MeResponse> recoveryConfirm(@Valid @RequestBody CredentialsRequest req) {
        return session(auth.recoveryConfirm(req));
    }

    private ResponseEntity<MeResponse> session(User u) {
        String token = jwt.issue(u.getId(), u.getUsername());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, jwt.sessionCookie(token).toString())
                .body(new MeResponse(u.getUsername(), u.getPublicKey(), u.hasOwnership()));
    }
}
