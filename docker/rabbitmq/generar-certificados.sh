#!/bin/sh
# Local CA and server certificate for the broker's TLS listener (SEC-07). Development only: in
# the cloud the provider's certificate is signed by a public CA. The output is git-ignored.
#   ./docker/rabbitmq/generar-certificados.sh
set -eu
DIR="$(cd "$(dirname "$0")" && pwd)/certs"
mkdir -p "$DIR"
cd "$DIR"

openssl req -x509 -newkey rsa:3072 -nodes -days 825 -keyout ca.key -out ca.pem \
  -subj "/CN=Planta Libre CA local"
openssl req -newkey rsa:3072 -nodes -keyout servidor.key -out servidor.csr -subj "/CN=rabbitmq"
printf "subjectAltName=DNS:rabbitmq,DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth\n" > ext.cnf
openssl x509 -req -in servidor.csr -CA ca.pem -CAkey ca.key -CAcreateserial -days 825 \
  -extfile ext.cnf -out servidor.pem
rm -f servidor.csr ext.cnf ca.srl
# The broker runs as another user inside the container and must read its key.
chmod 644 servidor.key ca.pem servidor.pem
chmod 600 ca.key
echo "Certificados en $DIR (la app confía en ca.pem)"
