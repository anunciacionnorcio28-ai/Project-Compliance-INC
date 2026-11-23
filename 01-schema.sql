-- Privacy-First Micro-Blogging Platform: MySQL 8.0 schema
-- Every table is created with ENCRYPTION='Y' (InnoDB tablespace encryption, requires the keyring configured in docker-compose).
SET NAMES utf8mb4;

CREATE TABLE users (
  id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  username             VARCHAR(24)  NOT NULL UNIQUE,
  password_hash        VARCHAR(100) NOT NULL,               -- bcrypt of the system-generated password
  mfa_secret           VARCHAR(255) NOT NULL,               -- TOTP secret, AES-256-GCM encrypted by the application
  pending_mfa_secret   VARCHAR(255) NULL,                   -- new TOTP secret awaiting verification (MFA reset / recovery)
  mfa_verified         BOOLEAN      NOT NULL DEFAULT FALSE, -- registration completes only after first valid TOTP code
  last_totp_step       BIGINT       NOT NULL DEFAULT 0,     -- blocks TOTP code replay
  ownership_key_hash   CHAR(64)     NULL,                   -- SHA-256 fingerprint of the Owner Seed public key (NULL = opted out)
  ownership_public_key VARCHAR(64)  NULL,                   -- Ed25519 public key (base64) used to verify challenge signatures
  public_key           TEXT         NOT NULL,               -- RSA-OAEP public key (base64 SPKI) for end-to-end encrypted posts
  created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ENCRYPTION='Y';

CREATE TABLE media (
  id           CHAR(36)    NOT NULL PRIMARY KEY,
  owner_id     BIGINT      NOT NULL,
  content_type VARCHAR(32) NOT NULL,
  data         MEDIUMBLOB  NOT NULL,                        -- re-encoded image, EXIF stripped
  created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_media_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ENCRYPTION='Y';

CREATE TABLE micro_posts (
  id                BIGINT     NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id           BIGINT     NOT NULL,
  content_encrypted MEDIUMTEXT NOT NULL,                    -- plain text (<=280) for normal posts, client-side ciphertext envelope for E2EE posts
  is_public         BOOLEAN    NOT NULL DEFAULT TRUE,       -- FALSE = followers only
  is_e2ee           BOOLEAN    NOT NULL DEFAULT FALSE,      -- TRUE = content is a Web Crypto envelope the server cannot read
  media_id          CHAR(36)   NULL,
  like_count        INT        NOT NULL DEFAULT 0,          -- aggregated only, no per-user like records
  expires_at        TIMESTAMP  NULL DEFAULT NULL,           -- Time-to-Live
  created_at        TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_posts_user (user_id, id),
  KEY idx_posts_public (is_public, id),
  KEY idx_posts_expiry (expires_at),
  CONSTRAINT fk_posts_user  FOREIGN KEY (user_id)  REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_posts_media FOREIGN KEY (media_id) REFERENCES media(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ENCRYPTION='Y';

CREATE TABLE comments (
  id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  post_id    BIGINT        NOT NULL,
  user_id    BIGINT        NOT NULL,
  content    VARCHAR(1120) NOT NULL,
  is_public  BOOLEAN       NOT NULL DEFAULT FALSE,          -- FALSE = only post author and commenter can see it
  created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_comments_post (post_id, id),
  CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES micro_posts(id) ON DELETE CASCADE,
  CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ENCRYPTION='Y';

CREATE TABLE follows (
  id          BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
  follower_id BIGINT    NOT NULL,
  followee_id BIGINT    NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_follow (follower_id, followee_id),
  KEY idx_follow_followee (followee_id),
  CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_follow_followee FOREIGN KEY (followee_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ENCRYPTION='Y';
