#!/usr/bin/env sh
# Self-signed TLS certificate for local testing. In production, put a real certificate in ./certs
# as fullchain.pem + privkey.pem (for example from Let's Encrypt).
set -eu
cd "$(dirname "$0")/.."
mkdir -p certs
HOST="${1:-localhost}"
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 365 \
  -subj "/CN=$HOST" -addext "subjectAltName=DNS:$HOST,DNS:localhost,IP:127.0.0.1" \
  -keyout certs/privkey.pem -out certs/fullchain.pem
chmod 600 certs/privkey.pem
echo "Wrote certs/fullchain.pem and certs/privkey.pem for $HOST"
