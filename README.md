# Planta Libre — Backend

Sistema de reserva de espacios de trabajo. API REST en Spring Boot 3 (Java 21), PostgreSQL con
Flyway, cobro asincrónico por RabbitMQ y autenticación con JWT.

- Frontend: [Front-Desarrollo-de-Aplicaciones-II](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II)
- Documentación: [docs/DocumentoTecnico.md](docs/DocumentoTecnico.md) · [docs/Integracion.md](docs/Integracion.md) · [docs/DominioReservas.md](docs/DominioReservas.md)

## Levantar el sistema completo

Requisito: Docker con Docker Compose v2 (Docker Desktop en Windows y Mac). No hace falta instalar
Java, Node ni crear ningún archivo de configuración.

Clonar los dos repositorios en la misma carpeta, uno al lado del otro, y levantar desde el backend:

```bash
git clone https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II.git
git clone https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II.git
cd Back-Desarrollo-de-Aplicaciones-II
docker compose up --build
```

La primera vez tarda unos minutos porque compila el backend y el frontend. El sistema está listo
cuando el log muestra `Started BackDaiiApplication`.

| Qué | Dirección |
| --- | --- |
| Aplicación web | http://localhost:8088 |
| API (chequeo de salud) | http://localhost:8080/api/health |
| Consola de RabbitMQ | http://localhost:15672 (usuario `planta-libre-admin`) |

Qué levanta `docker compose up`:

1. `init`: genera contraseñas y claves aleatorias (base, JWT, cifrado, broker, cuentas demo) y los
   certificados TLS del broker. Ningún secreto está en el repositorio.
2. `postgres`: la base de datos. Flyway crea el esquema y los datos de catálogo al arrancar la API.
3. `rabbitmq` y `rabbitmq-usuarios`: el broker, solo con TLS y con un usuario propio para la app.
4. `back`: la API, con el cobro asincrónico activado.
5. `front`: la aplicación web, servida por nginx, que reenvía `/api` al backend.

Si alguno de esos puertos ya está ocupado en la máquina, se cambia al levantar (también se puede
dejar en `.env`):

```bash
FRONT_PUERTO=9000 API_PUERTO=9080 docker compose up --build
```

Para apagarlo: `docker compose down`. Para empezar de cero (borra la base y los secretos
generados): `docker compose down -v`.

## Cuentas para probar

Las cuentas de demostración usan el dominio `@demo.plantalibre.local` (por ejemplo
`ana.ruiz@demo.plantalibre.local`). Su contraseña se genera en el primer arranque; para verla:

```bash
docker compose exec back cat /secretos/demo_password
```

Las cuentas nuevas se confirman por mail. Como en local no hay credenciales de correo, el link de
confirmación aparece en el log del backend:

```bash
docker compose logs back
```

La cuenta de administrador entra con contraseña y segundo factor (TOTP). Sus credenciales no están
en el repositorio y se entregan por separado a la cátedra.

## Desarrollo

Para correr la API desde el IDE contra la base y el broker de los contenedores:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up postgres rabbitmq rabbitmq-usuarios
```

Después copiar `.env.example` a `.env` y completar los valores. Las variables que también estén en
`.env` (por ejemplo `DB_PASSWORD`) se usan en lugar de las generadas, siempre que se definan antes
del primer arranque.

Pruebas automatizadas y análisis estático (SpotBugs con FindSecBugs):

```bash
mvn verify
```

El reporte de cobertura queda en `target/site/jacoco/index.html`. El pipeline de CI
(`.github/workflows/ci.yml`) corre lo mismo en cada push y pull request. Si el repositorio tiene
configurado SonarCloud (variables `SONAR_ORGANIZATION` y `SONAR_PROJECT_KEY` y el secreto
`SONAR_TOKEN`), también publica el análisis ahí.

Si el frontend está clonado en otra carpeta, se indica con `FRONT_CONTEXT` (también se puede
dejar en `.env`):

```bash
FRONT_CONTEXT=../Front docker compose up --build
```
