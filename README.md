# Hush: Privacy-First Micro-Blogging Platform

Spring Boot (stateless API) + Angular (standalone) + MySQL 8 + Docker Compose, built from the project SRS.

## Run it

```sh
sh scripts/gen-secrets.sh        # creates .env with random secrets
sh scripts/gen-certs.sh          # self-signed TLS cert for local testing (use a real cert in production)
docker compose up -d --build     # open https://localhost
docker compose --profile tor up -d --build   # optional: also serve as a Tor onion service
docker compose exec tor cat /var/lib/tor/hidden_service/hostname
```
For an onion-only deployment over plain HTTP set `COOKIE_SECURE=false` in `.env`.

Local development: run MySQL, the backend (`mvn spring-boot:run` with the env vars from `application.yml`) and `npm install && npm start` in `frontend/` (proxies `/api` to port 8080).

## SRS coverage

| SRS item | Where |
|---|---|
| 3.1 Factor 1: generated 32-char password (SecureRandom, bound to username via HMAC expansion) | `crypto/PasswordGenerator`, bcrypt hash in `users.password_hash` |
| 3.1 Factor 2: TOTP, secret/QR at registration, code at every login | `crypto/TotpService` (googleauth), `crypto/QrService` (ZXing, server-side QR so no external service) |
| Mandatory TOTP verification at registration | `POST /api/auth/register/verify`; unverified accounts can't sign in and are purged after 1 h |
| 3.1 Optional Owner Seed (Tier 3), 256-bit via SecureRandom, 32-word mnemonic | `service/OwnershipService`, wizard option + Settings |
| Owner Seed guards MFA reset, hard deletion (challenge-response, Ed25519 signature, single use, 2 min) | `AccountService.stepUp`, `shared/step-up.component.ts` |
| No recovery pathways; recovery only through Owner Seed | `/api/auth/recovery/*`, `features/recover.component.ts` |
| No emails/PII, no IP logging, no session tracking | no access logs (Tomcat and nginx), `X-Forwarded-For` blanked, throttling keyed by username only, stateless JWT |
| JWT in HttpOnly cookie, issued only after both factors | `security/JwtService` (HttpOnly, SameSite=Strict, Secure) |
| 3.2 Client-side E2EE with Web Crypto | `core/crypto.service.ts` (AES-GCM content key wrapped per follower with RSA-OAEP; private key kept non-extractable in IndexedDB) |
| 280-character limit | enforced in `PostService` and the composer |
| EXIF/metadata stripping | `service/MediaService` decodes and re-encodes every JPEG/PNG |
| Self-destructing posts (TTL) + cleanup task | `expires_at`, `service/CleanupService` (every minute) |
| 3.3 Private comments (author + commenter unless opened) | `PostService.comments` |
| Incognito reactions (aggregate counts only) | `like_count` incremented atomically, no like rows |
| 4 No third-party analytics/fonts/CDNs | system font stacks, Angular `analytics:false`, `fonts.inline:false` |
| CSP | `frontend/security-headers.conf` (script-src 'self'), API CSP `default-src 'none'` |
| Open-source, auditable | whole repository |
| Encrypted DB volumes | InnoDB tablespace encryption (`ENCRYPTION='Y'`, keyring volume) + AES-256-GCM for TOTP secrets |
| Encrypted off-site backups | `scripts/backup.sh` / `restore.sh` (GPG AES-256 + rclone/scp) |
| Security Best Practices guide + FOSS password manager recommendation (Bitwarden, KeePassXC) | banner after registration, `/security` page |
| 5.2 Credential export: "Download Secure Identity File" | registration wizard final step |
| SRI for all local scripts | `subresourceIntegrity: true` in `angular.json` |
| 5.3 Internal Docker network, nginx TLS 1.3 + HSTS, Tor-ready | `docker-compose.yml` (`internal: true` network), `docker/nginx`, `docker/tor` |
| 6 Schema | `docker/mysql/01-schema.sql` |

The SRS schema stopped mid-table at `Micro_Posts`; the remaining columns (`is_e2ee`, `media_id`, `like_count`, `expires_at`, `created_at`) and the `comments`, `follows`, `media` tables are my completion. Extra `users` columns support the features above: `pending_mfa_secret`, `mfa_verified`, `last_totp_step` (TOTP replay block), `ownership_public_key`.

## Design notes and limits

- Likes can't be de-duplicated server-side without recording who liked, so the "already liked" state lives only in the browser.
- Encrypted posts are readable by the people who follow the author at posting time. They can't carry images (the server can't strip metadata it can't read).
- E2EE private keys live on the device (and in the identity file). A new device needs the identity file imported in Settings.
- Owner Seed signing uses WebCrypto Ed25519 (Chrome/Edge 137+, Firefox 129+, Safari 17+).
- Phone-photo EXIF orientation is dropped with the rest of the metadata, so some images may appear rotated.
- Logout clears the cookie; the stateless JWT itself expires after `SESSION_MINUTES`.
- I wrote this without being able to compile or run it here. Expect to fix small build errors on the first `docker compose build`.
