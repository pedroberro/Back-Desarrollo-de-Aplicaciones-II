#!/bin/sh
# One-shot setup run by `docker compose up` before anything else, so a clean clone starts with
# no manual step and no secret in the repository (SEC-10):
#   - /secretos: random secrets for the database, the API and the broker, kept in a named volume.
#     A value already present in the environment (e.g. from a developer's .env) wins.
#   - /certs: local CA and server certificate for the broker's TLS listener (SEC-07).
# Both are generated only once: later runs reuse what is there.
set -eu

SECRETOS=/secretos
CERTS=/certs
mkdir -p "$SECRETOS" "$CERTS"

# 64 hex characters: URL-safe (it goes inside RABBITMQ_URL) and accepted by Secretos.requerido,
# which rejects values containing sequences like 1234.
aleatorio() {
  while :; do
    valor=$(openssl rand -hex 32)
    case "$valor" in *1234*) ;; *) echo "$valor"; return ;; esac
  done
}

# secreto NAME [value from the environment]
secreto() {
  archivo="$SECRETOS/$(echo "$1" | tr 'A-Z' 'a-z')"
  if [ ! -s "$archivo" ]; then
    if [ -n "${2:-}" ]; then printf '%s' "$2" > "$archivo"; else aleatorio | tr -d '\n' > "$archivo"; fi
  fi
  # Read by the postgres, rabbitmq and curl images, which run as different users.
  chmod 644 "$archivo"
}

secreto DB_PASSWORD "${DB_PASSWORD:-}"
secreto JWT_SECRET "${JWT_SECRET:-}"
secreto CIFRADO_CLAVE_ACTUAL "${CIFRADO_CLAVE_ACTUAL:-}"
secreto MENSAJERIA_FIRMA_SECRETO "${MENSAJERIA_FIRMA_SECRETO:-}"
secreto RABBITMQ_ADMIN_PASSWORD "${RABBITMQ_ADMIN_PASSWORD:-}"
secreto RABBITMQ_APP_PASSWORD "${RABBITMQ_APP_PASSWORD:-}"
secreto DEMO_PASSWORD "${DEMO_PASSWORD:-}"

leer() { cat "$SECRETOS/$1"; }

# What the API sources on startup (see the back service in docker-compose.yml).
cat > "$SECRETOS/env" <<EOF
DB_PASSWORD=$(leer db_password)
JWT_SECRET=$(leer jwt_secret)
CIFRADO_CLAVE_ACTUAL=$(leer cifrado_clave_actual)
MENSAJERIA_FIRMA_SECRETO=$(leer mensajeria_firma_secreto)
DEMO_PASSWORD=$(leer demo_password)
RABBITMQ_URL=amqps://planta-libre-api:$(leer rabbitmq_app_password)@rabbitmq:5671/planta-libre
EOF
chmod 644 "$SECRETOS/env"

if [ ! -s "$CERTS/servidor.pem" ]; then
  cd "$CERTS"
  openssl req -x509 -newkey rsa:3072 -nodes -days 825 -keyout ca.key -out ca.pem \
    -subj "/CN=Planta Libre CA local" 2>/dev/null
  openssl req -newkey rsa:3072 -nodes -keyout servidor.key -out servidor.csr \
    -subj "/CN=rabbitmq" 2>/dev/null
  printf "subjectAltName=DNS:rabbitmq,DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth\n" > ext.cnf
  openssl x509 -req -in servidor.csr -CA ca.pem -CAkey ca.key -CAcreateserial -days 825 \
    -extfile ext.cnf -out servidor.pem 2>/dev/null
  rm -f servidor.csr ext.cnf ca.srl
  chmod 644 servidor.key ca.pem servidor.pem
  chmod 600 ca.key
fi

echo "Inicializacion lista: secretos en el volumen 'secretos', certificados del broker en docker/rabbitmq/certs"
