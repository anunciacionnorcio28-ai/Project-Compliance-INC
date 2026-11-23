#!/usr/bin/env sh
# Encrypted off-site backup: mysqldump -> gzip -> GPG (AES-256, symmetric) -> upload.
#
# Required env:  BACKUP_PASSPHRASE_FILE   file holding the encryption passphrase (keep it OFF the server's backup target)
#                BACKUP_TARGET            rclone remote path (e.g. offsite:hush-backups) or scp target (user@host:/path)
# Optional:      BACKUP_METHOD=rclone|scp (default rclone), KEEP_LOCAL=1 to keep the encrypted file locally
# Run from cron, e.g.  17 3 * * *  cd /opt/hush && BACKUP_PASSPHRASE_FILE=... BACKUP_TARGET=... scripts/backup.sh
set -eu
cd "$(dirname "$0")/.."
: "${BACKUP_PASSPHRASE_FILE:?set BACKUP_PASSPHRASE_FILE}"
: "${BACKUP_TARGET:?set BACKUP_TARGET}"
. ./.env
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$(mktemp -d)/hush-$STAMP.sql.gz.gpg"
umask 077
docker compose exec -T db sh -c 'mysqldump --single-transaction --no-tablespaces -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  | gzip -9 \
  | gpg --batch --yes --pinentry-mode loopback --passphrase-file "$BACKUP_PASSPHRASE_FILE" --symmetric --cipher-algo AES256 -o "$OUT"
case "${BACKUP_METHOD:-rclone}" in
  rclone) rclone copyto "$OUT" "$BACKUP_TARGET/$(basename "$OUT")" ;;
  scp)    scp -q "$OUT" "$BACKUP_TARGET/" ;;
  *) echo "unknown BACKUP_METHOD" >&2; exit 2 ;;
esac
[ "${KEEP_LOCAL:-0}" = "1" ] && echo "kept $OUT" || rm -rf "$(dirname "$OUT")"
echo "backup $STAMP uploaded"
