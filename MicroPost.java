package app.privmicro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "micro_posts")
@Getter
@Setter
@NoArgsConstructor
public class MicroPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** Plain text for normal posts; client-side Web Crypto envelope (JSON) for end-to-end encrypted posts. */
    @Column(name = "content_encrypted", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String contentEncrypted;

    @Column(name = "is_public", nullable = false)
    private boolean publicPost;

    @Column(name = "is_e2ee", nullable = false)
    private boolean e2ee;

    @Column(name = "media_id")
    private String mediaId;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
