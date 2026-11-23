#!/usr/bin/env sh
# Creates .env with fresh random secrets. Refuses to overwrite an existing file.
set -eu
cd "$(dirname "$0")/.."
[ -e .env ] && { echo ".env already exists; delete it first if you really want new secrets (existing data would become unreadable)."; exit 1; }
rand() { openssl rand -base64 "$1" | tr -d '/+=\n'; }
cat > .env <<ENV
MYSQL_ROOT_PASSWORD=$(rand 33)
MYSQL_DATABASE=privmicro
MYSQL_USER=privmicro
MYSQL_PASSWORD=$(rand 33)
JWT_SECRET=$(rand 48)
DATA_ENCRYPTION_KEY=$(openssl rand -base64 32)
COOKIE_SECURE=true
SESSION_MINUTES=120
ENV
chmod 600 .env
echo "Created .env. Back it up somewhere safe: DATA_ENCRYPTION_KEY is needed to read stored authenticator secrets."
