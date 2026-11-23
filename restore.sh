#!/usr/bin/env sh
# Restore an encrypted backup:  scripts/restore.sh hush-XXXX.sql.gz.gpg  (needs BACKUP_PASSPHRASE_FILE)
set -eu
cd "$(dirname "$0")/.."
: "${BACKUP_PASSPHRASE_FILE:?set BACKUP_PASSPHRASE_FILE}"
FILE="${1:?usage: restore.sh <backup.sql.gz.gpg>}"
gpg --batch --pinentry-mode loopback --passphrase-file "$BACKUP_PASSPHRASE_FILE" --decrypt "$FILE" | gunzip \
  | docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
echo "restored $FILE"
