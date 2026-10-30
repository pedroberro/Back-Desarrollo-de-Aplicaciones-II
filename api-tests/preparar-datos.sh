#!/usr/bin/env bash
# Prepares a freshly started API for the Postman collection and writes its environment file.
# The data a client cannot create through the API alone (links that arrive by mail, the
# administrator's password) goes straight to the database. Every value is random and lives only
# in this run.
#
# Needs: the API on BASE_URL (default http://localhost:8080), psql reaching its database through
# PGHOST/PGUSER/PGPASSWORD/PGDATABASE, python3 with bcrypt, and ADMIN_PASSWORD and DEMO_PASSWORD
# (the same one the API was started with).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
DIR="$(cd "$(dirname "$0")" && pwd)"
SALIDA="$DIR/out"
mkdir -p "$SALIDA"

: "${ADMIN_PASSWORD:?Falta ADMIN_PASSWORD}"
: "${DEMO_PASSWORD:?Falta DEMO_PASSWORD}"

aleatorio() { openssl rand -hex 16; }
sha256() { printf '%s' "$1" | sha256sum | cut -d' ' -f1; }
sql() { psql -v ON_ERROR_STOP=1 -qtAc "$1"; }

registrar() {
  curl -fsS -o /dev/null -X POST "$BASE_URL/api/auth/register" -H 'Content-Type: application/json' \
    -d "{\"nombre\":\"$2\",\"email\":\"$1\",\"telefono\":\"+54 11 5555 0000\",\"password\":\"$3\",\"rol\":\"CLIENTE\"}"
}

# The administrator signs in with a password known only to this run, and enrols a new second factor.
ADMIN_HASH=$(python3 -c 'import bcrypt, sys; print(bcrypt.hashpw(sys.argv[1].encode(), bcrypt.gensalt(12)).decode())' "$ADMIN_PASSWORD")
sql "UPDATE usuarios SET password = '$ADMIN_HASH', segundo_factor_activo = FALSE, segundo_factor_secreto = NULL,
       segundo_factor_ultimo_paso = NULL WHERE email = 'admin@plantalibre.local'"

# A customer still pending confirmation, with a confirmation link the collection uses.
NUEVO_EMAIL="postman-nuevo-$(aleatorio)@pruebas.plantalibre.local"
NUEVO_CLAVE="Clave-$(aleatorio)-7"
registrar "$NUEVO_EMAIL" "Cliente Postman" "$NUEVO_CLAVE"
CONFIRMACION=$(aleatorio)$(aleatorio)
sql "INSERT INTO email_confirmation_tokens (usuario_id, token_hash)
       SELECT id, '$(sha256 "$CONFIRMACION")' FROM usuarios WHERE email = '$NUEVO_EMAIL'"

# A confirmed customer with a password recovery link.
RECUPERA_EMAIL="postman-recupera-$(aleatorio)@pruebas.plantalibre.local"
registrar "$RECUPERA_EMAIL" "Cliente que recupera" "Clave-$(aleatorio)-7"
RECUPERACION=$(aleatorio)$(aleatorio)
sql "UPDATE usuarios SET estado = 'ACTIVO' WHERE email = '$RECUPERA_EMAIL'"
sql "INSERT INTO password_reset_tokens (usuario_id, token_hash, expires_at)
       SELECT id, '$(sha256 "$RECUPERACION")', NOW() + INTERVAL '30 minutes' FROM usuarios WHERE email = '$RECUPERA_EMAIL'"

python3 - "$SALIDA/entorno.json" <<PY
import json, sys
valores = {
    "baseUrl": "$BASE_URL",
    "adminEmail": "admin@plantalibre.local",
    "adminPassword": "$ADMIN_PASSWORD",
    "demoEmail": "ana.ruiz@demo.plantalibre.local",
    "demoPassword": "$DEMO_PASSWORD",
    "nuevoEmail": "$NUEVO_EMAIL",
    "nuevoClave": "$NUEVO_CLAVE",
    "confirmToken": "$CONFIRMACION",
    "recuperaEmail": "$RECUPERA_EMAIL",
    "resetToken": "$RECUPERACION",
}
json.dump({"name": "Planta Libre - corrida de CI",
           "values": [{"key": k, "value": v, "type": "secret" if "Password" in k or "Token" in k or "Clave" in k else "default",
                       "enabled": True} for k, v in valores.items()]},
          open(sys.argv[1], "w"), indent=2)
PY
echo "Datos de prueba listos: $SALIDA/entorno.json"
