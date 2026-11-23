package app.privmicro.security;

import app.privmicro.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {
    public static final String COOKIE = "pm_session";
    private final SecretKey key;
    private final Duration ttl;
    private final boolean secureCookie;

    public JwtService(AppProperties props) throws Exception {
        byte[] k = MessageDigest.getInstance("SHA-256").digest(("jwt|" + props.jwtSecret()).getBytes(StandardCharsets.UTF_8));
        this.key = Keys.hmacShaKeyFor(k);
        this.ttl = Duration.ofMinutes(props.sessionMinutes());
        this.secureCookie = props.cookieSecure();
    }

    /** Only called after BOTH factors have been validated. Token carries no IP or device data. */
    public String issue(long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder().subject(Long.toString(userId)).claim("u", username)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(ttl)))
                .signWith(key).compact();
    }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthUser(Long.parseLong(c.getSubject()), c.get("u", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public ResponseCookie sessionCookie(String token) {
        return ResponseCookie.from(COOKIE, token).httpOnly(true).secure(secureCookie).sameSite("Strict")
                .path("/api").maxAge(ttl).build();
    }

    public ResponseCookie clearCookie() {
        return ResponseCookie.from(COOKIE, "").httpOnly(true).secure(secureCookie).sameSite("Strict")
                .path("/api").maxAge(0).build();
    }
}
