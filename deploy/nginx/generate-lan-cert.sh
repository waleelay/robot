#!/bin/sh
set -eu

LAN_HOST="${1:?usage: sh generate-lan-cert.sh <lan-ip-or-hostname> [output-directory]}"
CERT_DIR="${2:-$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/certs}"
TMP_CONFIG="$(mktemp)"
trap 'rm -f "$TMP_CONFIG"' EXIT

case "$LAN_HOST" in
  *[!0-9.]*)
    SUBJECT_ALT_NAME="DNS:$LAN_HOST"
    ;;
  *)
    SUBJECT_ALT_NAME="IP:$LAN_HOST"
    ;;
esac

mkdir -p "$CERT_DIR"
cat > "$TMP_CONFIG" <<EOF
[req]
distinguished_name = dn
x509_extensions = ext
prompt = no

[dn]
CN = $LAN_HOST

[ext]
subjectAltName = $SUBJECT_ALT_NAME
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
basicConstraints = critical, CA:FALSE
EOF

openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -keyout "$CERT_DIR/server.key" \
  -out "$CERT_DIR/server.crt" \
  -config "$TMP_CONFIG"

printf 'Generated certificate: %s/server.crt\n' "$CERT_DIR"
printf 'Trust server.crt on every browser device before allowing microphone access.\n'
