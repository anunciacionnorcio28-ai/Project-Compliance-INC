package app.privmicro.model;

import app.privmicro.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 24)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "mfa_secret", nullable = false)
    private String mfaSecret;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "pending_mfa_secret")
    private String pendingMfaSecret;

    @Column(name = "mfa_verified", nullable = false)
    private boolean mfaVerified;

    @Column(name = "last_totp_step", nullable = false)
    private long lastTotpStep;

    @Column(name = "ownership_key_hash")
    private String ownershipKeyHash;

    @Column(name = "ownership_public_key")
    private String ownershipPublicKey;

    @Column(name = "public_key", nullable = false)
    private String publicKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean hasOwnership() {
        return ownershipPublicKey != null;
    }
}
