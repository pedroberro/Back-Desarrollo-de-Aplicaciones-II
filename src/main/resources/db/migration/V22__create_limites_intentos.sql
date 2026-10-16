-- Rate-limit and failed-attempt counters (SEC-04, SEC-06). In the database instead of in memory,
-- so every instance sees the same count and a restart does not reset a lockout.
CREATE TABLE limites_intentos (
    clave    VARCHAR(200) PRIMARY KEY,
    inicio   TIMESTAMP    NOT NULL,
    cantidad INT          NOT NULL
);

CREATE INDEX idx_limites_intentos_inicio ON limites_intentos (inicio);
